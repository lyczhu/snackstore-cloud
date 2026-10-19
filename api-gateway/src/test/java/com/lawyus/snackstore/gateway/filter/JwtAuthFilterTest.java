package com.lawyus.snackstore.gateway.filter;

import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.common.util.JwtUtil;
import com.lawyus.snackstore.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 网关鉴权过滤器的特征化测试。
 *
 * <p>网关是全系统唯一的身份签发/校验点：下游五个服务直接信任网关注入的 X-User-* 头而不验签，
 * 所以「白名单必须剥离伪造身份头」「admin 模式必须挡住普通用户」「会话吊销必须生效」这三类规则
 * 只有在这里拦得住。测试用真实 JwtUtil 与代码默认的 GatewaySecurityProperties（等价于 Nacos 默认值），
 * 只 mock Redis。
 */
@DisplayName("JwtAuthFilter 信任边界")
class JwtAuthFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final long USER_ID = 42L;

    private JwtUtil jwtUtil;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private JwtAuthFilter filter;
    private RecordingChain chain;
    private MockServerWebExchange exchange;

    /** 记录被放行的 exchange，用于断言身份头注入与剥离 */
    private static final class RecordingChain implements GatewayFilterChain {
        private ServerWebExchange passed;
        private int calls;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.passed = exchange;
            this.calls++;
            return Mono.empty();
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60_000L);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        filter = new JwtAuthFilter(jwtUtil, redisTemplate, new GatewaySecurityProperties());
        chain = new RecordingChain();
    }

    private void run(HttpMethod method, String path, Map<String, String> headers) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .remoteAddress(new InetSocketAddress("10.0.0.9", 51234));
        headers.forEach(builder::header);
        exchange = MockServerWebExchange.from(builder.build());
        filter.filter(exchange, chain).block();
    }

    private int status() {
        return exchange.getResponse().getStatusCode().value();
    }

    private String responseBody() {
        return exchange.getResponse().getBodyAsString().block();
    }

    private String tokenFor(String role) {
        return jwtUtil.generateToken(USER_ID, "张三", role);
    }

    /** 模拟 Redis 中保存的当前有效会话 token */
    private void sessionStores(String storedToken) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:" + USER_ID)).thenReturn(Mono.just(storedToken));
    }

    private void authorizedAs(String role, HttpMethod method, String path) {
        String token = tokenFor(role);
        sessionStores(token);
        run(method, path, Map.of("Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("白名单放行，且客户端伪造的 X-User-* 身份头被剥离")
    void whitelistStripsForgedIdentityHeaders() {
        run(HttpMethod.POST, "/api/auth/login", Map.of(
                "X-User-Id", "999",
                "X-Username", "attacker",
                "X-User-Role", "admin"));

        assertThat(chain.calls).isEqualTo(1);
        ServerHttpRequest forwarded = chain.passed.getRequest();
        assertThat(forwarded.getHeaders())
                .doesNotContainKey("X-User-Id")
                .doesNotContainKey("X-Username")
                .doesNotContainKey("X-User-Role");
    }

    @Test
    @DisplayName("白名单是「方法:路径」双匹配：同路径换方法就必须鉴权")
    void whitelistMatchesMethodAndPathTogether() {
        run(HttpMethod.GET, "/api/auth/login", Map.of());

        assertThat(chain.calls).isZero();
        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("缺少 Authorization 头直接 401")
    void missingAuthorizationHeaderIsRejected() {
        run(HttpMethod.GET, "/api/orders", Map.of());

        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(responseBody()).contains(String.valueOf(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("Bearer 前缀大小写不敏感")
    void bearerPrefixIsCaseInsensitive() {
        String token = tokenFor("user");
        sessionStores(token);

        run(HttpMethod.GET, "/api/orders", Map.of("Authorization", "bearer " + token));

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("过期令牌返回 TOKEN_EXPIRED，被篡改令牌返回 TOKEN_INVALID（前端处理不同）")
    void expiredAndTamperedTokensUseDifferentCodes() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L);
        String expired = shortLived.generateToken(USER_ID, "张三", "user");
        Thread.sleep(50L);

        run(HttpMethod.GET, "/api/orders", Map.of("Authorization", "Bearer " + expired));

        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(responseBody()).contains(String.valueOf(ResultCode.TOKEN_EXPIRED.getCode()));

        String token = tokenFor("user");
        String[] parts = token.split("\\.");
        String tamperedPayload = ("A".equals(parts[1].substring(0, 1)) ? "B" : "A") + parts[1].substring(1);
        run(HttpMethod.GET, "/api/orders",
                Map.of("Authorization", "Bearer " + parts[0] + "." + tamperedPayload + "." + parts[2]));

        assertThat(responseBody()).contains(String.valueOf(ResultCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("普通用户访问 admin-only 接口返回 403 且不转发下游")
    void nonAdminIsBlockedFromAdminOnlyPattern() {
        authorizedAs("user", HttpMethod.POST, "/api/products");

        assertThat(status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chain.calls).isZero();
        assertThat(responseBody()).contains(String.valueOf(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("角色比较大小写不敏感，ADMIN 放行并注入三个身份头")
    void adminRoleIsCaseInsensitiveAndInjectsHeaders() {
        authorizedAs("ADMIN", HttpMethod.POST, "/api/products");

        assertThat(chain.calls).isEqualTo(1);
        var headers = chain.passed.getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo(String.valueOf(USER_ID));
        assertThat(headers.getFirst("X-Username")).isEqualTo("张三");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("统计接口只允许管理员（GET:/api/statistics/** 属 admin-only）")
    void statisticsRequiresAdmin() {
        authorizedAs("user", HttpMethod.GET, "/api/statistics/dashboard");

        assertThat(status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(chain.calls).isZero();

        authorizedAs("ADMIN", HttpMethod.GET, "/api/statistics/dashboard");
        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("会话 token 与本次不符（顶号或被踢）返回 TOKEN_INVALID")
    void revokedSessionIsRejected() {
        String token = tokenFor("user");
        sessionStores("a-completely-different-token");

        run(HttpMethod.GET, "/api/orders", Map.of("Authorization", "Bearer " + token));

        assertThat(chain.calls).isZero();
        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(responseBody()).contains(String.valueOf(ResultCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("Redis 会话 key 不存在（禁用用户已被吊销）同样拒绝")
    void missingSessionKeyIsRejected() {
        String token = tokenFor("user");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:" + USER_ID)).thenReturn(Mono.empty());

        run(HttpMethod.GET, "/api/orders", Map.of("Authorization", "Bearer " + token));

        assertThat(chain.calls).isZero();
        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("Redis 故障时 fail-open 放行（有意取舍；本测试让这条规则不可被悄悄改掉）")
    void redisFailureFailsOpen() {
        String token = tokenFor("user");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:" + USER_ID)).thenReturn(Mono.error(new IllegalStateException("redis down")));

        run(HttpMethod.GET, "/api/orders", Map.of("Authorization", "Bearer " + token));

        assertThat(chain.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("商品读接口匿名可访问，库存写接口不在白名单（契约不对称的对照）")
    void productReadIsPublicWhileStockWriteIsNot() {
        run(HttpMethod.GET, "/api/products/1", Map.of());
        assertThat(chain.calls).isEqualTo(1);

        run(HttpMethod.POST, "/api/products/stock/batch-deductions", Map.of());
        assertThat(status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("过滤器顺序：IP 头覆写必须先于鉴权，鉴权为 -1")
    void filterOrderPutsIpRewriteFirst() {
        assertThat(filter.getOrder()).isEqualTo(-1);
        assertThat(new ClientIpHeaderFilter().getOrder()).isLessThan(filter.getOrder());
    }
}
