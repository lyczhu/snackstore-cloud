package com.lawyus.snackstore.statistics.client.order;

import java.util.List;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderClientFallbackFactory implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        log.error("OrderClient fallback triggered: {}", cause.getMessage(), cause);
        return new OrderClient() {
            @Override
            public Result<OrderStatisticsVO> getOrderStatistics() {
                return Result.failed(500, "OrderService unavailable");
            }

            @Override
            public Result<List<TrendVO>> getOrderTrend(int days) {
                return Result.failed(500, "OrderService unavailable");
            }

            @Override
            public Result<List<ProductSalesVO>> getProductSalesTop(int limit) {
                return Result.failed(500, "OrderService unavailable");
            }
        };
    }
}
