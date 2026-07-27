package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisVerificationCodePort implements VerificationCodePort {

    private static final String SMS_CODE_PREFIX = "sms:code:";

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodePort(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String phone) {
        return redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
    }

    @Override
    public void invalidate(String phone) {
        redisTemplate.delete(SMS_CODE_PREFIX + phone);
    }
}