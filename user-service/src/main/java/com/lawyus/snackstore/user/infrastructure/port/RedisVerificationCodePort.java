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
    private static final String SMS_VERIFY_FAIL_PREFIX = "sms:verify-fail:";
    private static final String SMS_IP_LIMIT_PREFIX = "sms:ip-limit:";

    private final StringRedisTemplate redisTemplate;

    @Value("${user.sms.send-interval-seconds:60}")
    private long sendIntervalSeconds;

    @Value("${user.sms.daily-max:10}")
    private long dailyMax;

    @Value("${user.sms.max-fail:5}")
    private long maxFail;

    @Value("${user.sms.fail-lock-minutes:30}")
    private long failLockMinutes;

    @Value("${user.sms.ip-hourly-max:10}")
    private long ipHourlyMax;

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
        String limitKey = SMS_SEND_LIMIT_PREFIX + phone;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", Duration.ofSeconds(sendIntervalSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        String dailyKey = SMS_SEND_DAILY_PREFIX + phone;
        String dailyCount = redisTemplate.opsForValue().get(dailyKey);
        if (dailyCount != null && Long.parseLong(dailyCount) >= dailyMax) {
            return false;
        }
        return true;
    }

    @Override
    public void markSendSuccess(String phone) {
        String dailyKey = SMS_SEND_DAILY_PREFIX + phone;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
    }

    @Override
    public boolean tryAcquireSendByIp(String ip) {
        String ipKey = SMS_IP_LIMIT_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(ipKey);
        if (count != null && count == 1) {
            redisTemplate.expire(ipKey, Duration.ofHours(1));
        }
        return count == null || count <= ipHourlyMax;
    }

    @Override
    public boolean isVerificationLocked(String phone) {
        String failCount = redisTemplate.opsForValue().get(SMS_VERIFY_FAIL_PREFIX + phone);
        return failCount != null && Long.parseLong(failCount) >= maxFail;
    }

    @Override
    public long incrementVerificationFail(String phone) {
        String failKey = SMS_VERIFY_FAIL_PREFIX + phone;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, Duration.ofMinutes(failLockMinutes));
        }
        if (count != null && count >= maxFail) {
            invalidate(phone);
        }
        return count != null ? count : 0L;
    }

    @Override
    public void resetVerificationFail(String phone) {
        redisTemplate.delete(SMS_VERIFY_FAIL_PREFIX + phone);
    }
}
