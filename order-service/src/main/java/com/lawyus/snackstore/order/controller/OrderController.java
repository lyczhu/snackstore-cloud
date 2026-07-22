package com.lawyus.snackstore.order.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.dto.OrderQueryDTO;
import com.lawyus.snackstore.order.model.vo.OrderVO;
import com.lawyus.snackstore.order.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderVO> createOrder(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(userId, dto));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(orderService.getOrderDetail(id, userId));
    }

    @GetMapping("/list")
    public Result<PageResult<OrderVO>> getOrderList(OrderQueryDTO queryDTO) {
        return Result.success(orderService.getOrderList(queryDTO));
    }

    @PutMapping("/{id}/pay")
    public Result<Void> payOrder(@PathVariable Long id,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        orderService.payOrder(id, userId);
        return Result.success(null);
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long userId) {
        orderService.cancelOrder(id, userId);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long userId) {
        orderService.deleteOrder(id, userId);
        return Result.success(null);
    }
}
