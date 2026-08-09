package com.lawyus.snackstore.order.controller;

import java.util.List;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.model.vo.OrderStatisticsVO;
import com.lawyus.snackstore.order.model.vo.OrderTrendVO;
import com.lawyus.snackstore.order.model.vo.ProductSalesVO;
import com.lawyus.snackstore.order.service.OrderInternalService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/orders")
public class OrderInternalController {

    private final OrderInternalService orderInternalService;

    public OrderInternalController(OrderInternalService orderInternalService) {
        this.orderInternalService = orderInternalService;
    }

    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> getOrderStatistics() {
        return Result.success(orderInternalService.getOrderStatistics());
    }

    @GetMapping("/trend")
    public Result<List<OrderTrendVO>> getOrderTrend(
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return Result.success(orderInternalService.getOrderTrend(days));
    }

    @GetMapping("/top/products")
    public Result<List<ProductSalesVO>> getProductSalesTop(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return Result.success(orderInternalService.getProductSalesTop(limit));
    }
}
