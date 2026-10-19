package com.lawyus.snackstore.gateway.filter;

import com.lawyus.snackstore.common.constant.TraceConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求 id 生成的特征化测试：id 是跨服务日志串联的唯一线索，
 * 且必须与 X-User-*、X-Real-IP 一样遵循"客户端值一律不可信"的网关信任模型。
 */
@DisplayName("RequestTraceFilter 请求 id 生成与伪造防护")
class RequestTraceFilterTest {

    private static final Pattern GENERATED_ID = Pattern.compile("[0-9a-f]{32}");

    private final RequestTraceFilter filter = new RequestTraceFilter();
    private final RecordingChain chain = new RecordingChain();

    @Test
    @DisplayName("为请求生成一个 id，并注入下游请求头")
    void generatesAndInjectsRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/products").build());

        filter.filter(exchange, chain).block();

        String injected = chain.passed.getRequest().getHeaders().getFirst(TraceConstants.HEADER_REQUEST_ID);
        assertThat(injected).matches(GENERATED_ID);
        assertThat(RequestTraceFilter.currentRequestId(chain.passed)).isEqualTo(injected);
    }

    @Test
    @DisplayName("客户端自带的 X-Request-Id 被丢弃，下游只看到网关生成的值")
    void forgedRequestIdIsDiscarded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/orders")
                        .header(TraceConstants.HEADER_REQUEST_ID, "forged-by-client")
                        .build());

        filter.filter(exchange, chain).block();

        String injected = chain.passed.getRequest().getHeaders().getFirst(TraceConstants.HEADER_REQUEST_ID);
        assertThat(injected).isNotEqualTo("forged-by-client").matches(GENERATED_ID);
        // 多值伪造头同样只剩网关那一份，避免下游取到客户端的第一个值
        assertThat(chain.passed.getRequest().getHeaders()
                .get(TraceConstants.HEADER_REQUEST_ID)).hasSize(1);
    }

    @Test
    @DisplayName("响应头回写同一个 id，前端报障可直接定位日志")
    void responseHeaderEchoesSameId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/orders").build());

        filter.filter(exchange, chain).block();

        String injected = chain.passed.getRequest().getHeaders().getFirst(TraceConstants.HEADER_REQUEST_ID);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceConstants.HEADER_REQUEST_ID))
                .isEqualTo(injected);
    }

    @Test
    @DisplayName("两次请求的 id 不同，可区分并发请求")
    void requestIdIsPerRequest() {
        MockServerWebExchange first = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/products/1").build());
        MockServerWebExchange second = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/products/1").build());

        filter.filter(first, new RecordingChain()).block();
        filter.filter(second, new RecordingChain()).block();

        assertThat(RequestTraceFilter.currentRequestId(first))
                .isNotEqualTo(RequestTraceFilter.currentRequestId(second));
    }

    @Test
    @DisplayName("必须早于鉴权(-1)与访问日志(-2)，被拒绝的请求也留有可关联 id")
    void runsBeforeAuthAndAccessLog() {
        assertThat(filter.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
        assertThat(filter.getOrder()).isLessThan(new AccessLogFilter().getOrder());
        assertThat(filter.getOrder()).isLessThan(-1);
    }

    @Test
    @DisplayName("取不到 id 时返回占位符而不是 null")
    void currentRequestIdFallsBackToPlaceholder() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/actuator/health").build());

        assertThat(RequestTraceFilter.currentRequestId(exchange)).isEqualTo(RequestTraceFilter.UNKNOWN);
    }

    private static final class RecordingChain implements GatewayFilterChain {
        private ServerWebExchange passed;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.passed = exchange;
            return Mono.empty();
        }
    }
}
