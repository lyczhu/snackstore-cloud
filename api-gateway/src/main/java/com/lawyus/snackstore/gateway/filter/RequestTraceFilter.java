package com.lawyus.snackstore.gateway.filter;

import com.lawyus.snackstore.common.constant.TraceConstants;
import com.lawyus.snackstore.common.util.TraceIdUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 为每个入站请求生成一次请求 id，经请求头透传给下游，并回写响应头供前端/工单关联日志。
 *
 * <p>信任模型与 {@link ClientIpHeaderFilter}、{@link JwtAuthFilter} 一致：客户端自带的
 * {@code X-Request-Id} 一律丢弃，下游只会看到网关注入的值，无法用伪造 id 污染或冒充他人请求的日志。
 *
 * <p>网关是 WebFlux/Netty，一次请求的日志回调可能跨事件循环线程，线程本地的 MDC 无法保证
 * 与请求一一对应，因此这里不写 MDC，而是把 id 存进 {@link ServerWebExchange} 属性，
 * 由 {@link AccessLogFilter} 与 {@code GatewayExceptionHandler} 显式打进日志文本；
 * 下游是 Servlet，行与线程绑定，使用 MDC + 日志 pattern（见 nacos-config/application.yml）。
 */
@Component
public class RequestTraceFilter implements GlobalFilter, Ordered {

    /** exchange 属性键，供网关自身的日志取值 */
    public static final String REQUEST_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".REQUEST_ID";

    /** 取不到 id 时的占位（例如早于本过滤器就失败的请求） */
    public static final String UNKNOWN = "-";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = TraceIdUtil.generate();
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> h.remove(TraceConstants.HEADER_REQUEST_ID))
                .header(TraceConstants.HEADER_REQUEST_ID, requestId)
                .build();

        // 响应头回写：值由网关生成，不来自下游回传，因此不会把下游的 id 冲突带给客户端
        exchange.getResponse().getHeaders().set(TraceConstants.HEADER_REQUEST_ID, requestId);

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 读取当前请求 id，缺失时返回占位符，调用方无需判空 */
    public static String currentRequestId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value instanceof String requestId ? requestId : UNKNOWN;
    }

    @Override
    public int getOrder() {
        // 紧跟 ClientIpHeaderFilter(HIGHEST_PRECEDENCE) 之后，且必须先于鉴权(-1)与访问日志(-2)，
        // 保证被拒绝的请求也留有可关联的 id
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
