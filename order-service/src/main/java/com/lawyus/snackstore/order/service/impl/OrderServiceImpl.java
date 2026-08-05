package com.lawyus.snackstore.order.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.order.constant.OrderStatusConstants;
import com.lawyus.snackstore.order.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.feign.product.BatchStockDTO;
import com.lawyus.snackstore.order.feign.product.ProductClient;
import com.lawyus.snackstore.order.feign.product.ProductFeignDetailVO;
import com.lawyus.snackstore.order.feign.product.ProductVO;
import com.lawyus.snackstore.order.mapper.OrderItemMapper;
import com.lawyus.snackstore.order.mapper.OrderMapper;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.dto.OrderQueryDTO;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.entity.OrderItem;
import com.lawyus.snackstore.order.model.vo.OrderVO;
import com.lawyus.snackstore.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductClient productClient;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            ProductClient productClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productClient = productClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, OrderCreateDTO orderDto) {
        String orderNo = IdWorker.getIdStr();
        List<OrderItem> itemList = buildOrderItems(orderDto.getItems());

        BigDecimal totalAmount = itemList.stream()
                .map(item -> item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatusConstants.PENDING);
        order.setReceiverName(orderDto.getReceiverName());
        order.setReceiverPhone(orderDto.getReceiverPhone());
        order.setReceiverAddress(orderDto.getReceiverAddress());
        orderMapper.insert(order);

        itemList.forEach(item -> item.setOrderId(order.getId()));
        orderItemMapper.insert(itemList);

        Result<Boolean> stockResult = productClient.batchDeductStock(buildStockDTO(orderNo, itemList));
        if (stockResult == null || stockResult.getData() == null || !stockResult.getData()) {
            log.error("批量扣减库存失败, 订单号: {}", orderNo);
            throw BusinessExceptionEnum.STOCK_NOT_ENOUGH.getException();
        }

        return convertToVO(order);
    }

    private List<OrderItem> buildOrderItems(List<OrderCreateDTO.OrderItemDTO> orderItems) {
        List<Long> productIdList = orderItems.stream()
                .map(OrderCreateDTO.OrderItemDTO::getProductId).toList();
        Map<Long, Integer> productMap = orderItems.stream()
                .collect(Collectors.toMap(OrderCreateDTO.OrderItemDTO::getProductId,
                        OrderCreateDTO.OrderItemDTO::getQuantity));

        Result<List<ProductVO>> result = productClient.getProductsByIds(productIdList);
        if (result == null || result.getData() == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }

        List<ProductVO> productVOList = result.getData();
        for (ProductVO pVO : productVOList) {
            if (pVO.getStatus() == null || pVO.getStatus() == 0) {
                throw BusinessExceptionEnum.PRODUCT_OFF_SHELF.getException();
            }
        }
        if (productVOList.size() != orderItems.size()) {
            throw BusinessExceptionEnum.ORDER_CREATE_FAILED.getException("商品数量不匹配");
        }

        List<OrderItem> itemList = new ArrayList<>();
        for (ProductVO pVO : productVOList) {
            Integer quantity = productMap.get(pVO.getId());
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(pVO.getId());
            orderItem.setProductName(pVO.getName());
            orderItem.setProductImage(pVO.getCoverImage());
            orderItem.setProductPrice(pVO.getPrice());
            orderItem.setQuantity(quantity);
            itemList.add(orderItem);
        }
        return itemList;
    }

    private BatchStockDTO buildStockDTO(String orderNo, List<OrderItem> items) {
        BatchStockDTO batchDTO = new BatchStockDTO();
        batchDTO.setOrderNo(orderNo);
        List<BatchStockDTO.StockItemDTO> stockItems = items.stream().map(item -> {
            BatchStockDTO.StockItemDTO stockItem = new BatchStockDTO.StockItemDTO();
            stockItem.setProductId(item.getProductId());
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).toList();
        batchDTO.setItems(stockItems);
        return batchDTO;
    }

    @Override
    @SentinelResource("getOrderDetail")
    public OrderVO getOrderDetail(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(Order::getUserId, userId));
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
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatusConstants.PENDING)
                .set(Order::getStatus, OrderStatusConstants.COMPLETED)
                .set(Order::getPayTime, LocalDateTime.now()));
        if (rows == 0) {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getId, id)
                            .eq(Order::getUserId, userId));
            if (order == null) {
                throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
            }
            throw BusinessExceptionEnum.ORDER_CANNOT_PAY.getException();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, id)
                        .eq(Order::getUserId, userId));
        if (order == null) {
            throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatusConstants.PENDING)
                .set(Order::getStatus, OrderStatusConstants.CANCELLED));
        if (rows == 0) {
            throw BusinessExceptionEnum.ORDER_CANNOT_CANCEL.getException();
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));

        Result<Boolean> stockResult = productClient.batchRollbackStock(buildStockDTO(order.getOrderNo(), items));
        if (stockResult == null || stockResult.getData() == null || !stockResult.getData()) {
            log.error("取消订单回滚库存失败，事务回滚: orderId={}", id);
            throw BusinessExceptionEnum.ORDER_CANCEL_FAILED.getException("库存回滚失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id, Long userId) {
        int rows = orderMapper.delete(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatusConstants.CANCELLED));
        if (rows == 0) {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getId, id)
                            .eq(Order::getUserId, userId));
            if (order == null) {
                throw BusinessExceptionEnum.ORDER_NOT_FOUND.getException();
            }
            throw BusinessExceptionEnum.ORDER_STATUS_ERROR.getException("仅已取消的订单可以删除");
        }
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
