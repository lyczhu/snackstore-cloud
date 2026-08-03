package com.lawyus.snackstore.order.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.snackstore.order.mapper.OrderMapper;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.vo.OrderStatisticsVO;
import com.lawyus.snackstore.order.model.vo.OrderTrendVO;
import com.lawyus.snackstore.order.model.vo.ProductSalesVO;
import com.lawyus.snackstore.order.service.OrderInternalService;
import org.springframework.stereotype.Service;

@Service
public class OrderInternalServiceImpl implements OrderInternalService {

    private static final int ORDER_STATUS_CANCELLED = 2;

    private final OrderMapper orderMapper;

    public OrderInternalServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderStatisticsVO getOrderStatistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setOrderCount(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().ne(Order::getStatus, ORDER_STATUS_CANCELLED)));
        vo.setTodayOrderCount(countTodayOrders());
        return vo;
    }

    @Override
    public List<OrderTrendVO> getOrderTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime endTime = today.plusDays(1L).atStartOfDay();
        return orderMapper.selectOrderTrend(startTime, endTime);
    }

    @Override
    public List<ProductSalesVO> getProductSalesTop(int limit) {
        return orderMapper.selectProductSalesTop(limit);
    }

    private Long countTodayOrders() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .ne(Order::getStatus, ORDER_STATUS_CANCELLED)
                .ge(Order::getCreatedAt, startOfDay)
                .lt(Order::getCreatedAt, startOfNextDay);
        return orderMapper.selectCount(wrapper);
    }
}
