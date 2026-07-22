package com.lawyus.snackstore.order.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.dto.OrderQueryDTO;
import com.lawyus.snackstore.order.model.vo.OrderVO;

public interface OrderService {

    OrderVO createOrder(Long userId, OrderCreateDTO dto);

    OrderVO getOrderDetail(Long id, Long userId);

    PageResult<OrderVO> getOrderList(OrderQueryDTO queryDTO);

    void payOrder(Long id, Long userId);

    void cancelOrder(Long id, Long userId);

    void deleteOrder(Long id, Long userId);
}
