package com.lawyus.snackstore.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.common.util.JwtUtil;
import com.lawyus.snackstore.gateway.config.GatewaySecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_KEY_PREFIX = "token:";

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySecurityProperties securityProperties;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         ReactiveStringRedisTemplate redisTemplate,
                         GatewaySecurityProperties securityProperties) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteListed(path, request.getMethod().name())) {
            // 白名单路径未做认证：剥离客户端伪造的身份头，下游服务不得信任未注入的身份
            return chain.filter(stripIdentityHeaders(exchange));
        }

        String token = extractToken(request);
        if (token == null) {
            log.warn("请求缺少token: {} {}", request.getMethod(), path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            String role = claims.get("role", String.class);

            if (requiresAdmin(request.getMethod().name(), path) && !isAdmin(role)) {
                Long userId = claims.get("userId", Long.class);
                log.warn("非管理员访问管理接口: userId={}, path={}", userId, path);
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
            }

            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(AuthConstants.HEADER_USER_ID, String.valueOf(userId))
                    .header(AuthConstants.HEADER_USERNAME, username)
                    .header(AuthConstants.HEADER_USER_ROLE, role)
                    .build();
            ServerWebExchange mutatedExchange = exchange.mutate().request(modifiedRequest).build();

            return validateSession(token, userId)
                    .flatMap(valid -> {
                        if (valid) {
                            return chain.filter(mutatedExchange);
                        }
                        log.warn("会话已失效(用户被禁用或已在新设备登录): userId={}", userId);
                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_INVALID);
                    });
        } catch (ExpiredJwtException e) {
            log.warn("token已过期: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.warn("token无效: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhiteListed(String path, String method) {
        return securityProperties.getWhitelist().stream().anyMatch(entry -> {
            int idx = entry.indexOf(':');
            if (idx < 0) {
                return PATH_MATCHER.match(entry, path);
            }
            String m = entry.substring(0, idx);
            String p = entry.substring(idx + 1);
            return m.equals(method) && PATH_MATCHER.match(p, path);
        });
    }

    /** 移除客户端传入的身份头，防止白名单路径上伪造 X-User-* 被下游误信 */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getHeaders().containsKey(AuthConstants.HEADER_USER_ID)
                && !request.getHeaders().containsKey(AuthConstants.HEADER_USERNAME)
                && !request.getHeaders().containsKey(AuthConstants.HEADER_USER_ROLE)) {
            return exchange;
        }
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(AuthConstants.HEADER_USER_ID);
                    h.remove(AuthConstants.HEADER_USERNAME);
                    h.remove(AuthConstants.HEADER_USER_ROLE);
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private boolean requiresAdmin(String method, String path) {
        if (securityProperties.getAdminPrefixPatterns().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return true;
        }
        return securityProperties.getAdminOnlyPatterns().stream().anyMatch(entry -> {
            int idx = entry.indexOf(':');
            if (idx < 0) {
                return PATH_MATCHER.match(entry, path);
            }
            String m = entry.substring(0, idx);
            String p = entry.substring(idx + 1);
            return m.equals(method) && PATH_MATCHER.match(p, path);
        });
    }

    private boolean isAdmin(String role) {
        return AuthConstants.ROLE_ADMIN.equalsIgnoreCase(role);
    }

    /**
     * 校验会话: Redis 中 token:{userId} 必须与当前 token 一致(禁用用户已被吊销该 key)。
     * Redis 不可用时按放行处理(fail-open)，避免网关整体不可用——期间被禁用/被踢用户的 token
     * 仍可访问，风险已在 docs/系统审查报告.md 登记；如不能接受请在 JWT 之外增加网关级兜底。
     */
    private Mono<Boolean> validateSession(String token, Long userId) {
        return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userId)
                .map(token::equals)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("[安全告警] Redis 会话校验异常，fail-open 放行，吊销检查失效: userId={}, err={}",
                            userId, e.getMessage());
                    return Mono.just(true);
                });
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || authHeader.length() < BEARER_PREFIX.length()
                || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, ResultCode resultCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> result = Result.failed(resultCode);
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}