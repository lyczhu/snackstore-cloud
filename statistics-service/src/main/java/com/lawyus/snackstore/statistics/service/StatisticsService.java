package com.lawyus.snackstore.statistics.service;

import java.util.List;

import com.lawyus.snackstore.statistics.model.vo.CategorySalesVO;
import com.lawyus.snackstore.statistics.model.vo.DashboardVO;
import com.lawyus.snackstore.statistics.model.vo.ProductSalesVO;
import com.lawyus.snackstore.statistics.model.vo.TrendVO;

public interface StatisticsService {

    DashboardVO getDashboard();

    List<TrendVO> getOrderTrend(int days);

    List<ProductSalesVO> getTopProducts(int limit);

    List<CategorySalesVO> getTopCategories(int limit);
}
