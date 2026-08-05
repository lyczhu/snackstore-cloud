package com.lawyus.snackstore.order.controller;

import org.springframework.web.bind.annotation.*;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.constant.OrderStatusConstants;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.dto.OrderQueryDTO;
import com.lawyus.snackstore.order.model.vo.OrderVO;
import com.lawyus.snackstore.order.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderVO> createOrder(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(userId, dto));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id,
                                          @RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(orderService.getOrderDetail(id, userId));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> getOrderList(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                    @Valid OrderQueryDTO queryDTO) {
        queryDTO.setUserId(userId);
        return Result.success(orderService.getOrderList(queryDTO));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateOrderStatus(@PathVariable Long id,
                                         @RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                         @RequestParam Integer status) {
        // status: 1=已支付(COMPLETED) 2=已取消(CANCELLED)
        if (status != null && status == OrderStatusConstants.COMPLETED) {
            orderService.payOrder(id, userId);
        } else if (status != null && status == OrderStatusConstants.CANCELLED) {
            orderService.cancelOrder(id, userId);
        } else {
            throw new IllegalArgumentException("不支持的订单状态值: " + status);
        }
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id,
                                    @RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        orderService.deleteOrder(id, userId);
        return Result.success(null);
    }
}
