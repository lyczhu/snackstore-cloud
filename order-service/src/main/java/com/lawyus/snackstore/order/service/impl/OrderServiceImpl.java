package com.lawyus.snackstore.order.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.feign.product.ProductClient;
import com.lawyus.snackstore.order.feign.product.ProductFeignDetailVO;
import com.lawyus.snackstore.order.mapper.OrderItemMapper;
import com.lawyus.snackstore.order.mapper.OrderMapper;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.dto.OrderQueryDTO;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.entity.OrderItem;
import com.lawyus.snackstore.order.model.vo.DashboardVO;
import com.lawyus.snackstore.order.model.vo.OrderVO;
import com.lawyus.snackstore.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_COMPLETED = 1;
    private static final int ORDER_STATUS_CANCELLED = 2;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductClient productFeignClient;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            ProductClient productFeignClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productFeignClient = productFeignClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, OrderCreateDTO dto) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setTotalAmount(totalAmount);
        order.setStatus(ORDER_STATUS_PENDING);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        orderMapper.insert(order);

        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            Result<ProductFeignDetailVO> productResult = productFeignClient.getProductDetail(itemDTO.getProductId());
            if (productResult == null || productResult.getData() == null) {
                throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
            }

            ProductFeignDetailVO product = productResult.getData();
            String productName = product.getName();
            String productImage = product.getCoverImage();
            BigDecimal productPrice = product.getPrice();
            Integer productStatus = product.getStatus();

            if (productStatus == null || productStatus == 0) {
                throw BusinessExceptionEnum.PRODUCT_OFF_SHELF.getException();
            }

            Result<Boolean> stockResult = productFeignClient.deductStock(itemDTO.getProductId(), itemDTO.getQuantity());
            if (stockResult == null || stockResult.getData() == null || !stockResult.getData()) {
                throw BusinessExceptionEnum.STOCK_NOT_ENOUGH.getException();
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(itemDTO.getProductId());
            orderItem.setProductName(productName);
            orderItem.setProductImage(productImage);
            orderItem.setProductPrice(productPrice);
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItemMapper.insert(orderItem);

            totalAmount = totalAmount.add(productPrice.multiply(BigDecimal.valueOf(itemDTO.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderMapper.updateById(order);

        return convertToVO(order);
    }

    @Override
    @SentinelResource("getOrderDetail")
    public OrderVO getOrderDetail(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(userId != null, Order::getUserId, userId));
        if (order == null) {
            throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
        }
        return convertToVO(order);
    }

    @Override
    @SentinelResource("getOrderList")
    public PageResult<OrderVO> getOrderList(OrderQueryDTO queryDTO) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Order::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getUserId() != null) {
            wrapper.eq(Order::getUserId, queryDTO.getUserId());
        }
        wrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<Order> result = orderMapper.selectPage(page, wrapper);

        return PageResult.success(
                result.getRecords().stream().map(this::convertToVO).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(userId != null, Order::getUserId, userId));
        if (order == null) {
            throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
        }
        if (order.getStatus() != ORDER_STATUS_PENDING) {
            throw BusinessExceptionEnum.ORDER_CANNOT_PAY.getException();
        }
        order.setStatus(ORDER_STATUS_COMPLETED);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(userId != null, Order::getUserId, userId));
        if (order == null) {
            throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
        }
        if (order.getStatus() != ORDER_STATUS_PENDING) {
            throw BusinessExceptionEnum.ORDER_CANNOT_CANCEL.getException();
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        for (OrderItem item : items) {
            productFeignClient.rollbackStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(ORDER_STATUS_CANCELLED);
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(userId != null, Order::getUserId, userId));
        if (order == null) {
            throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
        }
        orderMapper.deleteById(id);
    }

    @Override
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();
        Long orderCount = orderMapper.selectCount(null);
        Long todayOrderCount = orderMapper.countTodayOrders();
        vo.setOrderCount(orderCount);
        vo.setTodayOrderCount(todayOrderCount);
        vo.setProductCount(0L);
        vo.setUserCount(0L);
        return vo;
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp + random;
    }

    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setPayTime(order.getPayTime());
        vo.setCreatedAt(order.getCreatedAt());

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        List<OrderVO.OrderItemVO> itemVOs = items.stream().map(item -> {
            OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setProductName(item.getProductName());
            itemVO.setProductImage(item.getProductImage());
            itemVO.setProductPrice(item.getProductPrice());
            itemVO.setQuantity(item.getQuantity());
            return itemVO;
        }).toList();
        vo.setItems(itemVOs);

        return vo;
    }
}
