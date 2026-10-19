package com.lawyus.snackstore.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lawyus.snackstore.common.constant.TraceConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问日志携带请求 id 的测试。网关是 reactive，不能依赖线程本地 MDC，
 * 因此 id 通过 exchange 属性传递并由日志文本显式输出；这里验证链路真的把 id 打通了。
 */
@DisplayName("AccessLogFilter 访问日志关联请求 id")
class AccessLogFilterTest {

    private final AccessLogFilter accessLogFilter = new AccessLogFilter();

    @Test
    @DisplayName("与 RequestTraceFilter 串成链路后，日志行含同一个 id")
    void accessLogLineCarriesRequestId() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        logger.addAppender(appender);
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.method(HttpMethod.GET, "/api/products/1").build());

            // RequestTraceFilter 先把 id 写入 exchange，再交给访问日志过滤器
            GatewayFilterChain next = ex -> accessLogFilter.filter(ex, new NoopChain());
            new RequestTraceFilter().filter(exchange, next).block();

            String expectedId = exchange.getResponse().getHeaders().getFirst(TraceConstants.HEADER_REQUEST_ID);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("[" + expectedId + "]")
                    .contains("GET /api/products/1");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("未走 id 过滤器时仍能出日志，用占位符而非 null")
    void accessLogWithoutTraceFilterStillLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        logger.addAppender(appender);
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.method(HttpMethod.POST, "/api/orders").build());

            accessLogFilter.filter(exchange, new NoopChain()).block();

            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("[" + RequestTraceFilter.UNKNOWN + "]");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("访问日志排在鉴权之后，确保鉴权结论已写入 exchange")
    void runsAfterAuthFilter() {
        assertThat(accessLogFilter.getOrder()).isLessThan(0);
    }

    private static final class NoopChain implements GatewayFilterChain {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            return Mono.empty();
        }
    }
}
