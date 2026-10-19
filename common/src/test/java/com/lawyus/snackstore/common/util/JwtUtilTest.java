package com.lawyus.snackstore.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 的特征化测试：网关是全系统唯一的身份签发点，下游只信网关注入的头，
 * 因此「签名校验真的在起作用」与「过期一定被判为无效」是全站鉴权的地基。
 */
@DisplayName("JwtUtil 签名/过期/密钥强度")
class JwtUtilTest {

    /** 32 个 ASCII 字符 = 32 字节，刚好满足 HS256 下限 */
    private static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";
    private static final long EXPIRATION_MILLIS = 60_000L;

    private static JwtUtil jwtUtil() {
        return new JwtUtil(SECRET_32_BYTES, EXPIRATION_MILLIS);
    }

    @Test
    @DisplayName("拒绝空 secret、缺配置与非正的过期时间")
    void constructorRejectsMissingOrInvalidConfig() {
        assertThatThrownBy(() -> new JwtUtil(null, EXPIRATION_MILLIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtUtil("  ", EXPIRATION_MILLIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret");
        assertThatThrownBy(() -> new JwtUtil(SECRET_32_BYTES, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.expiration");
    }

    @Test
    @DisplayName("secret 强度按字节而非字符数校验（HS256 要求）")
    void secretLengthIsMeasuredInBytesNotChars() {
        // 31 字节：不足下限
        assertThatThrownBy(() -> new JwtUtil(SECRET_32_BYTES.substring(0, 31), EXPIRATION_MILLIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");

        // 11 个中文字符 = 33 字节（UTF-8 每字 3 字节）：字符数不到 32 但字节数够，必须放行
        String cjkSecret = "零" + "零食电商微服务后端令牌密钥".substring(0, 10);
        assertThat(cjkSecret).hasSize(11);
        assertThat(cjkSecret.getBytes(StandardCharsets.UTF_8).length).isEqualTo(33);
        JwtUtil util = new JwtUtil(cjkSecret, EXPIRATION_MILLIS);
        assertThat(util.generateToken(1L, "user", "admin")).isNotBlank();

        // 10 个中文字符 = 30 字节：够 10 个字符但不足 32 字节，必须拒绝
        String tooShortCjk = cjkSecret.substring(0, 10);
        assertThat(tooShortCjk.getBytes(StandardCharsets.UTF_8).length).isLessThan(32);
        assertThatThrownBy(() -> new JwtUtil(tooShortCjk, EXPIRATION_MILLIS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("签发后解析可无损取回 userId/username/role/subject")
    void generateAndParseRoundTrip() {
        JwtUtil util = jwtUtil();

        String token = util.generateToken(42L, "张三", "admin");

        Claims claims = util.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("张三");
        assertThat(util.getUserId(token)).isEqualTo(42L);
        assertThat(util.getUsername(token)).isEqualTo("张三");
        assertThat(util.getRole(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("未过期令牌判定有效，且过期时间约等于 now + expirationMillis")
    void freshTokenIsValidAndExpiryMatchesConfig() {
        JwtUtil util = jwtUtil();

        String token = util.generateToken(1L, "shopper", "user");
        LocalDateTime expiry = util.getExpiration(token);

        assertThat(util.validateToken(token)).isTrue();
        assertThat(util.isTokenExpired(token)).isFalse();
        assertThat(expiry).isBetween(LocalDateTime.now().plusSeconds(50),
                LocalDateTime.now().plusSeconds(70));
    }

    @Test
    @DisplayName("过期令牌：isTokenExpired 与 validateToken 必须一致地判为失效")
    void expiredTokenIsRejected() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET_32_BYTES, 1L);

        String token = shortLived.generateToken(1L, "shopper", "user");
        Thread.sleep(50L);

        assertThat(shortLived.isTokenExpired(token)).isTrue();
        assertThat(shortLived.validateToken(token)).isFalse();
        assertThatThrownBy(() -> shortLived.getUserId(token)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("换密钥的令牌解析不通，被篡改的密文也解析不通")
    void foreignOrTamperedTokenIsRejected() {
        JwtUtil issuer = jwtUtil();
        JwtUtil otherIssuer = new JwtUtil("ffffffffffffffffffffffffffffffff", EXPIRATION_MILLIS);
        String token = issuer.generateToken(1L, "shopper", "admin");

        // 用别人的密钥验：签名不匹配
        assertThat(otherIssuer.validateToken(token)).isFalse();

        // 篡改载荷段（保持格式合法但签名与内容不符）
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + flip(parts[1]) + "." + parts[2];
        assertThat(issuer.validateToken(tampered)).isFalse();

        // 垃圾串也不能抛出未捕获异常
        assertThat(issuer.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("expirationMillis 通过 getter 暴露给会话缓存计算 TTL")
    void exposesExpirationMillis() {
        assertThat(jwtUtil().getExpirationMillis()).isEqualTo(EXPIRATION_MILLIS);
    }

    private static String flip(String segment) {
        char first = segment.charAt(0);
        return (first == 'A' ? 'B' : 'A') + segment.substring(1);
    }
}
