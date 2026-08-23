package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.user.domain.user.port.LoginLockPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLoginLockPort implements LoginLockPort {

    private static final String LOGIN_FAIL_PREFIX = "login:fail:";

    private final StringRedisTemplate redisTemplate;

    @Value("${user.login.max-fail:5}")
    private long maxFail;

    @Value("${user.login.fail-lock-minutes:30}")
    private long failLockMinutes;

    public RedisLoginLockPort(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isLocked(String phone) {
        String failCount = redisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + phone);
        return failCount != null && Long.parseLong(failCount) >= maxFail;
    }

    @Override
    public long incrementFail(String phone) {
        String failKey = LOGIN_FAIL_PREFIX + phone;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, Duration.ofMinutes(failLockMinutes));
        }
        return count != null ? count : 0L;
    }

    @Override
    public void resetFail(String phone) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + phone);
    }
}
