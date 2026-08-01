package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.common.util.JwtUtil;
import com.lawyus.snackstore.user.domain.user.port.TokenPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class JwtTokenPort implements TokenPort {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public JwtTokenPort(StringRedisTemplate redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String generate(Long userId, String phone, String role) {
        return jwtUtil.generateToken(userId, phone, role);
    }

    @Override
    public void store(Long userId, String token) {
        redisTemplate.opsForValue().set("token:" + userId, token, jwtUtil.getExpirationMillis(), TimeUnit.MILLISECONDS);
    }
}
