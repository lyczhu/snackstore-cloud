package com.lawyus.snackstore.statistics.client.order;

import java.util.List;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service", fallbackFactory = OrderClientFallbackFactory.class)
public interface OrderClient {

    @GetMapping("/internal/orders/statistics")
    Result<OrderStatisticsVO> getOrderStatistics();

    @GetMapping("/internal/orders/trend")
    Result<List<TrendVO>> getOrderTrend(@RequestParam("days") int days);

    @GetMapping("/internal/orders/top/products")
    Result<List<ProductSalesVO>> getProductSalesTop(@RequestParam("limit") int limit);
}
