package com.lawyus.snackstore.statistics.cache;

import com.lawyus.snackstore.statistics.constant.StatisticsCacheConstants;
import com.lawyus.snackstore.statistics.model.vo.DashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DashboardCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public DashboardVO get() {
        String key = currentKey();
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof DashboardVO dashboardVO) {
            return dashboardVO;
        }
        return null;
    }

    public void put(DashboardVO dashboardVO) {
        String key = currentKey();
        long jitteredTtl = StatisticsCacheConstants.jitteredTtlSeconds(
                StatisticsCacheConstants.DASHBOARD_CACHE_TTL_SECONDS);
        redisTemplate.opsForValue().set(key, dashboardVO, jitteredTtl, TimeUnit.SECONDS);
    }

    private String currentKey() {
        return StatisticsCacheConstants.dashboardKey(LocalDate.now());
    }
}
