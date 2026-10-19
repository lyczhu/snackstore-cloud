package com.lawyus.snackstore.product.config;

import com.lawyus.snackstore.common.constant.TraceConstants;
import com.lawyus.snackstore.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把网关注入的 X-Request-Id 放进 MDC，供 logging.pattern 里的 %X{requestId} 输出，
 * 使本服务日志能与网关及其他服务日志串联（见 nacos-config/application.yml）。
 *
 * <p>请求 id 不信任客户端：仅当形态合法（{@code [A-Za-z0-9_-]{1,64}}）时采用，
 * 缺失或非法（Feign 内部直连、运维端口、伪造头）时自行生成，同时阻断日志注入。
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = TraceIdUtil.resolveOrGenerate(request.getHeader(TraceConstants.HEADER_REQUEST_ID));
        MDC.put(TraceConstants.MDC_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.MDC_REQUEST_ID);
        }
    }
}
