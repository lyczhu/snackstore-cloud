package com.lawyus.snackstore.statistics.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存键与 TTL 抖动的特征化测试。缓存键一旦重叠会让不同查询条件互相串数据，
 * 抖动消失则等于失去防缓存雪崩的能力——两者都是「看起来正常、出事在半夜」的那类缺陷。
 */
@DisplayName("StatisticsCacheConstants 键隔离与 TTL 抖动")
class StatisticsCacheConstantsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @Test
    @DisplayName("看板 TTL 60 秒，趋势与 TOP 均为 300 秒")
    void ttlConstantsArePinned() {
        assertThat(StatisticsCacheConstants.DASHBOARD_CACHE_TTL_SECONDS).isEqualTo(60L);
        assertThat(StatisticsCacheConstants.TREND_CACHE_TTL_SECONDS).isEqualTo(300L);
        assertThat(StatisticsCacheConstants.TOP_CACHE_TTL_SECONDS).isEqualTo(300L);
    }

    @Test
    @DisplayName("抖动幅度为 ±10%，且确实产生离散值（否则缓存会同时过期）")
    void jitterStaysWithinTenPercentAndVaries() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            long ttl = StatisticsCacheConstants.jitteredTtlSeconds(300L);
            assertThat(ttl).isBetween(270L, 330L);
            seen.add(ttl);
        }
        assertThat(seen).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("TTL 小于 10 秒时抖动幅度为 0，不会把过期时间算成 0 或负数")
    void jitterDoesNotPushSmallTtlNonPositive() {
        assertThat(StatisticsCacheConstants.jitteredTtlSeconds(5L)).isEqualTo(5L);
        assertThat(StatisticsCacheConstants.jitteredTtlSeconds(60L)).isPositive();
    }

    @Test
    @DisplayName("不同查询条件与不同业务维度必须落在不同的键上")
    void keysAreIsolatedByDimensionAndParameters() {
        String trend7 = StatisticsCacheConstants.trendKey(DAY, 7);
        String trend30 = StatisticsCacheConstants.trendKey(DAY, 30);
        String topProducts = StatisticsCacheConstants.topProductsKey(DAY, 10);
        String topCategories = StatisticsCacheConstants.topCategoriesKey(DAY, 10);
        String dashboard = StatisticsCacheConstants.dashboardKey(DAY);

        assertThat(Set.of(trend7, trend30, topProducts, topCategories, dashboard)).hasSize(5);
        assertThat(trend7).endsWith(":2026-10-19:7");
        assertThat(dashboard).endsWith(":2026-10-19");
    }

    @Test
    @DisplayName("跨天自动换键，昨天的缓存不会被今天读到")
    void keysChangeWithDate() {
        assertThat(StatisticsCacheConstants.dashboardKey(LocalDate.of(2026, 10, 18)))
                .isNotEqualTo(StatisticsCacheConstants.dashboardKey(LocalDate.of(2026, 10, 19)));
    }
}
