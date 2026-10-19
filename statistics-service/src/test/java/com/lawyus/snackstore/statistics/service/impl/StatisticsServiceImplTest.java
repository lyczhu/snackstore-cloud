package com.lawyus.snackstore.statistics.service.impl;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.statistics.cache.DashboardCacheService;
import com.lawyus.snackstore.statistics.cache.StatisticsCacheService;
import com.lawyus.snackstore.statistics.client.order.OrderClient;
import com.lawyus.snackstore.statistics.client.order.OrderStatisticsVO;
import com.lawyus.snackstore.statistics.client.product.ProductCategoryVO;
import com.lawyus.snackstore.statistics.client.product.ProductClient;
import com.lawyus.snackstore.statistics.client.user.UserClient;
import com.lawyus.snackstore.statistics.constant.StatisticsCacheConstants;
import com.lawyus.snackstore.statistics.exception.BusinessException;
import com.lawyus.snackstore.statistics.model.vo.CategorySalesVO;
import com.lawyus.snackstore.statistics.model.vo.DashboardVO;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 统计聚合的特征化测试。
 *
 * <p>统计口径的三条铁律必须钉住：只统计已完成订单的数值由下游 SQL 决定（本服务不重算）、
 * 任一上游返回非 200 就整体失败而**不写缓存**（否则错误数据会被缓存住）、缓存命中时不得再打下游。
 * 聚合执行器换成同线程直发执行器，保证断言确定性。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsServiceImpl 聚合口径与缓存语义")
class StatisticsServiceImplTest {

    @Mock
    private OrderClient orderClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private UserClient userClient;

    @Mock
    private DashboardCacheService dashboardCacheService;

    @Mock
    private StatisticsCacheService statisticsCacheService;

    private StatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StatisticsServiceImpl(orderClient, productClient, userClient,
                dashboardCacheService, statisticsCacheService, Runnable::run);
    }

    private static OrderStatisticsVO orderStats(long total, long today) {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setOrderCount(total);
        vo.setTodayOrderCount(today);
        return vo;
    }

    private static ProductSalesVO sales(long productId, long quantity, String amount) {
        ProductSalesVO vo = new ProductSalesVO();
        vo.setProductId(productId);
        vo.setProductName("商品-" + productId);
        vo.setQuantity(quantity);
        vo.setAmount(new BigDecimal(amount));
        return vo;
    }

    private static ProductCategoryVO category(long id, String name) {
        ProductCategoryVO vo = new ProductCategoryVO();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    @Test
    @DisplayName("看板缓存命中时直接返回，不打任何下游服务")
    void dashboardCacheHitSkipsAllFeignCalls() {
        DashboardVO cached = new DashboardVO();
        when(dashboardCacheService.get()).thenReturn(cached);

        assertThat(service.getDashboard()).isSameAs(cached);
        verifyNoInteractions(orderClient, productClient, userClient);
    }

    @Test
    @DisplayName("缓存缺失：三路上游汇总后写缓存")
    void dashboardAggregatesThreeServicesAndCaches() {
        when(dashboardCacheService.get()).thenReturn(null);
        when(orderClient.getOrderStatistics()).thenReturn(Result.success(orderStats(100L, 7L)));
        when(productClient.countProducts()).thenReturn(Result.success(55L));
        when(userClient.countUsers()).thenReturn(Result.success(9L));

        DashboardVO vo = service.getDashboard();

        assertThat(vo.getOrderCount()).isEqualTo(100L);
        assertThat(vo.getTodayOrderCount()).isEqualTo(7L);
        assertThat(vo.getProductCount()).isEqualTo(55L);
        assertThat(vo.getUserCount()).isEqualTo(9L);
        verify(dashboardCacheService).put(vo);
    }

    @Test
    @DisplayName("锁内二次检查命中缓存时不再取数（单飞语义）")
    void dashboardDoubleChecksCacheInsideLock() {
        DashboardVO appeared = new DashboardVO();
        when(dashboardCacheService.get()).thenReturn(null, appeared);

        assertThat(service.getDashboard()).isSameAs(appeared);
        verifyNoInteractions(orderClient, productClient, userClient);
    }

    @Test
    @DisplayName("任一上游返回非 200：整体失败且不写缓存（不做部分降级，也不固化错误数据）")
    void dashboardFailsFastWithoutCachingWhenUpstreamNot200() {
        when(dashboardCacheService.get()).thenReturn(null);
        when(orderClient.getOrderStatistics()).thenReturn(Result.failed(ResultCode.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(service::getDashboard)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单服务");

        verify(dashboardCacheService, never()).put(any());
    }

    @Test
    @DisplayName("上游返回 200 但 data 为 null：按空值处理并写缓存（口径边界，避免误判为异常）")
    void dashboardAcceptsNullPayload() {
        when(dashboardCacheService.get()).thenReturn(null);
        when(orderClient.getOrderStatistics()).thenReturn(Result.success(orderStats(0L, 0L)));
        when(productClient.countProducts()).thenReturn(Result.success(null));
        when(userClient.countUsers()).thenReturn(Result.success(null));

        DashboardVO vo = service.getDashboard();

        assertThat(vo.getProductCount()).isNull();
        assertThat(vo.getOrderCount()).isZero();
        verify(dashboardCacheService).put(vo);
    }

    @Test
    @DisplayName("趋势缓存命中时不调订单服务")
    void trendUsesCacheWhenPresent() {
        String key = StatisticsCacheConstants.trendKey(LocalDate.now(), 7);
        List<TrendVO> cached = List.of(new TrendVO());
        when(statisticsCacheService.get(key, Object.class)).thenReturn(cached);

        assertThat(service.getOrderTrend(7)).isSameAs(cached);
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("趋势缓存缺失：取数并按 TREND TTL 写回，键里带天数")
    void trendLoadsAndCachesWithItsOwnKey() {
        String key = StatisticsCacheConstants.trendKey(LocalDate.now(), 30);
        List<TrendVO> loaded = List.of(new TrendVO(), new TrendVO());
        when(orderClient.getOrderTrend(30)).thenReturn(Result.success(loaded));

        assertThat(service.getOrderTrend(30)).isEqualTo(loaded);

        verify(statisticsCacheService)
                .put(eq(key), eq(loaded), eq(StatisticsCacheConstants.TREND_CACHE_TTL_SECONDS));
    }

    @Test
    @DisplayName("分类销量聚合：同分类合并、按销量倒序、无分类映射的商品被丢弃")
    void topCategoriesMergeSortAndDropUnmappedProducts() {
        when(orderClient.getProductSalesTop(5)).thenReturn(Result.success(List.of(
                sales(1L, 3, "30.00"),
                sales(2L, 5, "50.00"),
                sales(3L, 9, "90.00"),
                sales(4L, 100, "999.00"))));
        when(productClient.getProductCategoryMap())
                .thenReturn(Result.success(Map.of(1L, 10L, 2L, 10L, 3L, 20L)));
        when(productClient.getCategories())
                .thenReturn(Result.success(List.of(category(10L, "膨化"), category(20L, "饼干"))));

        List<CategorySalesVO> result = service.getTopCategories(5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategoryId()).isEqualTo(20L);
        assertThat(result.get(0).getQuantity()).isEqualTo(9L);
        assertThat(result.get(1).getCategoryId()).isEqualTo(10L);
        assertThat(result.get(1).getQuantity()).isEqualTo(8L);
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("80.00");
        assertThat(result.get(1).getCategoryName()).isEqualTo("膨化");
    }

    @Test
    @DisplayName("分类销量受 limit 截断，未知分类回退为「未知分类」")
    void topCategoriesRespectsLimitAndFallsBackName() {
        when(orderClient.getProductSalesTop(1)).thenReturn(Result.success(List.of(
                sales(1L, 3, "30.00"), sales(5L, 20, "200.00"))));
        when(productClient.getProductCategoryMap()).thenReturn(Result.success(Map.of(1L, 10L, 5L, 30L)));
        when(productClient.getCategories()).thenReturn(Result.success(List.of(category(10L, "膨化"))));

        List<CategorySalesVO> result = service.getTopCategories(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryId()).isEqualTo(30L);
        assertThat(result.get(0).getCategoryName()).isEqualTo("未知分类");
    }

    @Test
    @DisplayName("分类聚合中商品服务不可用：整体失败且不写缓存")
    void topCategoriesFailsWhenProductServiceUnavailable() {
        when(orderClient.getProductSalesTop(10)).thenReturn(Result.success(List.of(sales(1L, 1, "1.00"))));
        when(productClient.getProductCategoryMap()).thenReturn(Result.failed(ResultCode.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.getTopCategories(10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品服务");

        verify(statisticsCacheService, never()).put(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
