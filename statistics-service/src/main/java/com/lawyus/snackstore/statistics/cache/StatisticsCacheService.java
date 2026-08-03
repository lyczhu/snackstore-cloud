package com.lawyus.snackstore.statistics.cache;

import com.lawyus.snackstore.statistics.constant.StatisticsCacheConstants;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatisticsCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public StatisticsCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public void put(String key, Object value, long ttlSeconds) {
        long jitteredTtl = StatisticsCacheConstants.jitteredTtlSeconds(ttlSeconds);
        redisTemplate.opsForValue().set(key, value, jitteredTtl, TimeUnit.SECONDS);
    }
}
