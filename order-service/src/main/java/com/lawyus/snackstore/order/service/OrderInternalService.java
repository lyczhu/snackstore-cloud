package com.lawyus.snackstore.order.service;

import java.util.List;

import com.lawyus.snackstore.order.model.vo.OrderStatisticsVO;
import com.lawyus.snackstore.order.model.vo.OrderTrendVO;
import com.lawyus.snackstore.order.model.vo.ProductSalesVO;

public interface OrderInternalService {

    OrderStatisticsVO getOrderStatistics();

    List<OrderTrendVO> getOrderTrend(int days);

    List<ProductSalesVO> getProductSalesTop(int limit);
}
