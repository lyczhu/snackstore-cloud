package com.lawyus.snackstore.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端 IP 覆写的特征化测试：下游的短信 IP 限流依赖这里给出的可信 IP，
 * 若客户端能自带代理头，就能绕过按 IP 的频控。
 */
@DisplayName("ClientIpHeaderFilter 伪造代理头防护")
class ClientIpHeaderFilterTest {

    private final ClientIpHeaderFilter filter = new ClientIpHeaderFilter();
    private final RecordingChain chain = new RecordingChain();

    private static final class RecordingChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {
        private ServerWebExchange passed;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.passed = exchange;
            return Mono.empty();
        }
    }

    @Test
    @DisplayName("客户端自带的 X-Forwarded-For / X-Real-IP 被 TCP 对端地址覆盖")
    void forgedProxyHeadersAreDiscarded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/auth/sms-code")
                        .remoteAddress(new InetSocketAddress("203.0.113.7", 44770))
                        .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2")
                        .header("X-Real-IP", "1.1.1.1")
                        .build());

        filter.filter(exchange, chain).block();

        var headers = chain.passed.getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-For")).isEqualTo("203.0.113.7");
        assertThat(headers.getFirst("X-Real-IP")).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("拿不到对端地址时写 unknown，而不是留下客户端伪造值")
    void unknownAddressReplacesForgedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/auth/sms-code")
                        .header("X-Real-IP", "1.1.1.1")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(chain.passed.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("必须排在鉴权之前，否则鉴权日志里的 IP 不可信")
    void runsBeforeAuthFilter() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
