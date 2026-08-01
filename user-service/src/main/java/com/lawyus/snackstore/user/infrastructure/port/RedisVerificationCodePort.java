package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisVerificationCodePort implements VerificationCodePort {

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String SMS_SEND_LIMIT_PREFIX = "sms:send-limit:";
    private static final String SMS_SEND_DAILY_PREFIX = "sms:send-daily:";

    private final StringRedisTemplate redisTemplate;

    @Value("${user.sms.send-interval-seconds:60}")
    private long sendIntervalSeconds;

    @Value("${user.sms.daily-max:10}")
    private long dailyMax;

    public RedisVerificationCodePort(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String phone) {
        return redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
    }

    @Override
    public void save(String phone, String code, Duration ttl) {
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, code, ttl);
    }

    @Override
    public void invalidate(String phone) {
        redisTemplate.delete(SMS_CODE_PREFIX + phone);
    }

    @Override
    public boolean tryAcquireSend(String phone) {
        String dailyKey = SMS_SEND_DAILY_PREFIX + phone;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        if (dailyCount != null && dailyCount > dailyMax) {
            return false;
        }
        String limitKey = SMS_SEND_LIMIT_PREFIX + phone;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", Duration.ofSeconds(sendIntervalSeconds));
        return Boolean.TRUE.equals(acquired);
    }
}
