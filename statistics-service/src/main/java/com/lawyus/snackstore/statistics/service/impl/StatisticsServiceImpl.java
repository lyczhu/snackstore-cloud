package com.lawyus.snackstore.statistics.service.impl;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.statistics.cache.DashboardCacheService;
import com.lawyus.snackstore.statistics.cache.StatisticsCacheService;
import com.lawyus.snackstore.statistics.client.order.OrderClient;
import com.lawyus.snackstore.statistics.client.order.OrderStatisticsVO;
import com.lawyus.snackstore.statistics.client.product.ProductCategoryVO;
import com.lawyus.snackstore.statistics.client.product.ProductClient;
import com.lawyus.snackstore.statistics.client.user.UserClient;
import com.lawyus.snackstore.statistics.constant.StatisticsCacheConstants;
import com.lawyus.snackstore.statistics.exception.BusinessException;
import com.lawyus.snackstore.statistics.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.statistics.model.vo.CategorySalesVO;
import com.lawyus.snackstore.statistics.model.vo.DashboardVO;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;
import com.lawyus.snackstore.statistics.service.StatisticsService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final OrderClient orderClient;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final DashboardCacheService dashboardCacheService;
    private final StatisticsCacheService statisticsCacheService;
    private final Executor statisticsExecutor;

    public StatisticsServiceImpl(OrderClient orderClient,
                                 ProductClient productClient,
                                 UserClient userClient,
                                 DashboardCacheService dashboardCacheService,
                                 StatisticsCacheService statisticsCacheService,
                                 @Qualifier("statisticsExecutor") Executor statisticsExecutor) {
        this.orderClient = orderClient;
        this.productClient = productClient;
        this.userClient = userClient;
        this.dashboardCacheService = dashboardCacheService;
        this.statisticsCacheService = statisticsCacheService;
        this.statisticsExecutor = statisticsExecutor;
    }

    @Override
    public DashboardVO getDashboard() {
        DashboardVO cached = dashboardCacheService.get();
        if (cached != null) {
            return cached;
        }

        CompletableFuture<Result<OrderStatisticsVO>> orderFuture = CompletableFuture.supplyAsync(
                orderClient::getOrderStatistics, statisticsExecutor);
        CompletableFuture<Result<Long>> productFuture = CompletableFuture.supplyAsync(
                productClient::countProducts, statisticsExecutor);
        CompletableFuture<Result<Long>> userFuture = CompletableFuture.supplyAsync(
                userClient::countUsers, statisticsExecutor);

        try {
            CompletableFuture.allOf(orderFuture, productFuture, userFuture).join();

            OrderStatisticsVO orderStatistics = extractData(orderFuture.get(), "订单服务");
            Long productCount = extractData(productFuture.get(), "商品服务");
            Long userCount = extractData(userFuture.get(), "用户服务");

            DashboardVO vo = new DashboardVO();
            vo.setOrderCount(orderStatistics.getOrderCount());
            vo.setTodayOrderCount(orderStatistics.getTodayOrderCount());
            vo.setProductCount(productCount);
            vo.setUserCount(userCount);

            dashboardCacheService.put(vo);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException | ExecutionException e) {
            log.error("聚合 dashboard 数据失败", e);
            throw BusinessExceptionEnum.STATISTICS_SERVICE_ERROR.getException("统计服务暂不可用，请稍后重试");
        }
    }

    @Override
    public List<TrendVO> getOrderTrend(int days) {
        String key = StatisticsCacheConstants.trendKey(LocalDate.now(), days);
        List<TrendVO> cached = statisticsCacheService.get(key, List.class);
        if (cached != null) {
            return cached;
        }

        List<TrendVO> trend = extractData(orderClient.getOrderTrend(days), "订单服务");
        statisticsCacheService.put(key, trend, StatisticsCacheConstants.TREND_CACHE_TTL_SECONDS);
        return trend;
    }

    @Override
    public List<ProductSalesVO> getTopProducts(int limit) {
        String key = StatisticsCacheConstants.topProductsKey(LocalDate.now(), limit);
        List<ProductSalesVO> cached = statisticsCacheService.get(key, List.class);
        if (cached != null) {
            return cached;
        }

        List<ProductSalesVO> topProducts = extractData(orderClient.getProductSalesTop(limit), "订单服务");
        statisticsCacheService.put(key, topProducts, StatisticsCacheConstants.TOP_CACHE_TTL_SECONDS);
        return topProducts;
    }

    @Override
    public List<CategorySalesVO> getTopCategories(int limit) {
        String key = StatisticsCacheConstants.topCategoriesKey(LocalDate.now(), limit);
        List<CategorySalesVO> cached = statisticsCacheService.get(key, List.class);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<Result<List<ProductSalesVO>>> productsFuture = CompletableFuture.supplyAsync(
                () -> orderClient.getProductSalesTop(limit), statisticsExecutor);
        CompletableFuture<Result<Map<Long, Long>>> categoryMapFuture = CompletableFuture.supplyAsync(
                productClient::getProductCategoryMap, statisticsExecutor);
        CompletableFuture<Result<List<ProductCategoryVO>>> categoriesFuture = CompletableFuture.supplyAsync(
                productClient::getCategories, statisticsExecutor);

        try {
            CompletableFuture.allOf(productsFuture, categoryMapFuture, categoriesFuture).join();

            List<ProductSalesVO> productSales = extractData(productsFuture.get(), "订单服务");
            Map<Long, Long> productCategoryMap = extractData(categoryMapFuture.get(), "商品服务");
            List<ProductCategoryVO> categories = extractData(categoriesFuture.get(), "商品服务");

            List<CategorySalesVO> topCategories = aggregateCategorySales(productSales, productCategoryMap, categories, limit);
            statisticsCacheService.put(key, topCategories, StatisticsCacheConstants.TOP_CACHE_TTL_SECONDS);
            return topCategories;
        } catch (InterruptedException | ExecutionException e) {
            log.error("聚合分类销量数据失败", e);
            throw BusinessExceptionEnum.STATISTICS_SERVICE_ERROR.getException("统计服务暂不可用，请稍后重试");
        }
    }

    private List<CategorySalesVO> aggregateCategorySales(List<ProductSalesVO> productSales,
                                                         Map<Long, Long> productCategoryMap,
                                                         List<ProductCategoryVO> categories,
                                                         int limit) {
        Map<Long, String> categoryNameMap = categories.stream()
                .collect(Collectors.toMap(ProductCategoryVO::getId, ProductCategoryVO::getName));

        return productSales.stream()
                .filter(sales -> productCategoryMap.containsKey(sales.getProductId()))
                .collect(Collectors.groupingBy(
                        sales -> productCategoryMap.get(sales.getProductId()),
                        Collectors.reducing(
                                (a, b) -> {
                                    a.setQuantity(a.getQuantity() + b.getQuantity());
                                    a.setAmount(a.getAmount().add(b.getAmount()));
                                    return a;
                                })))
                .entrySet().stream()
                .map(entry -> {
                    Long categoryId = entry.getKey();
                    ProductSalesVO merged = entry.getValue().orElseThrow();
                    CategorySalesVO vo = new CategorySalesVO();
                    vo.setCategoryId(categoryId);
                    vo.setCategoryName(categoryNameMap.getOrDefault(categoryId, "未知分类"));
                    vo.setQuantity(merged.getQuantity());
                    vo.setAmount(merged.getAmount());
                    return vo;
                })
                .sorted(Comparator.comparing(CategorySalesVO::getQuantity).reversed())
                .limit(limit)
                .toList();
    }

    private <T> T extractData(Result<T> result, String serviceName) {
        if (result == null || result.getCode() == null || !Integer.valueOf(200).equals(result.getCode())) {
            log.error("{} 返回异常: code={}, message={}",
                    serviceName, result == null ? null : result.getCode(),
                    result == null ? null : result.getMessage());
            throw BusinessExceptionEnum.STATISTICS_SERVICE_ERROR
                    .getException(serviceName + "暂不可用，请稍后重试");
        }
        return result.getData();
    }
}
