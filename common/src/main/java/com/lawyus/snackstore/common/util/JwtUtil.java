package com.lawyus.snackstore.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class JwtUtil {

    private JwtUtil() {
    }

    private static final String SECRET_KEY = "snackstore-cloud-jwt-secret-key-for-mvp-xs3921n";
    private static final long ACCESS_TOKEN_EXPIRE_MINUTES = 120;
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(ACCESS_TOKEN_EXPIRE_MINUTES * 60);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public static String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_ROLE, String.class);
    }

    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException _) {
            return true;
        }
    }

    public static LocalDateTime getExpiration(String token) {
        Claims claims = parseToken(token);
        return LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault());
    }

    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception _) {
            return false;
        }
    }
}
