package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.common.util.JwtUtil;
import com.lawyus.snackstore.user.domain.user.port.TokenPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class JwtTokenPort implements TokenPort {

    private final StringRedisTemplate redisTemplate;

    public JwtTokenPort(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generate(Long userId, String phone, String role) {
        return JwtUtil.generateToken(userId, phone, role);
    }

    @Override
    public void store(Long userId, String token) {
        redisTemplate.opsForValue().set("token:" + userId, token, 2, TimeUnit.HOURS);
    }
}