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
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_USER_ROLE = "X-User-Role";
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
            return chain.filter(exchange);
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
                    .header(HEADER_USER_ID, String.valueOf(userId))
                    .header(HEADER_USERNAME, username)
                    .header(HEADER_USER_ROLE, role)
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
        return "admin".equalsIgnoreCase(role);
    }

    /**
     * 校验会话: Redis 中 token:{userId} 必须与当前 token 一致(禁用用户已被吊销该 key)。
     * Redis 不可用时按放行处理，避免网关整体不可用。
     */
    private Mono<Boolean> validateSession(String token, Long userId) {
        return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userId)
                .map(token::equals)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.warn("Redis会话校验异常，按放行处理: userId={}, err={}", userId, e.getMessage());
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