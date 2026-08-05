package com.lawyus.snackstore.statistics.controller;

import java.util.List;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.statistics.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.statistics.model.vo.CategorySalesVO;
import com.lawyus.snackstore.statistics.model.vo.DashboardVO;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;
import com.lawyus.snackstore.statistics.service.StatisticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard(@RequestHeader(AuthConstants.HEADER_USER_ROLE) String role) {
        assertAdmin(role);
        return Result.success(statisticsService.getDashboard());
    }

    @GetMapping("/trend")
    public Result<List<TrendVO>> getOrderTrend(
            @RequestHeader(AuthConstants.HEADER_USER_ROLE) String role,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        assertAdmin(role);
        return Result.success(statisticsService.getOrderTrend(days));
    }

    @GetMapping("/top/products")
    public Result<List<ProductSalesVO>> getTopProducts(
            @RequestHeader(AuthConstants.HEADER_USER_ROLE) String role,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        assertAdmin(role);
        return Result.success(statisticsService.getTopProducts(limit));
    }

    @GetMapping("/top/categories")
    public Result<List<CategorySalesVO>> getTopCategories(
            @RequestHeader(AuthConstants.HEADER_USER_ROLE) String role,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        assertAdmin(role);
        return Result.success(statisticsService.getTopCategories(limit));
    }

    private void assertAdmin(String role) {
        if (!AuthConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            throw BusinessExceptionEnum.ACCESS_FORBIDDEN.getException("仅管理员可查看统计数据");
        }
    }
}
