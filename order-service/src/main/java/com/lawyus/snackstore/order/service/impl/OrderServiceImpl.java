package com.lawyus.snackstore.order.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.lawyus.snackstore.order.model.vo.DashboardVO;
import com.lawyus.snackstore.order.model.vo.OrderVO;
import com.lawyus.snackstore.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_COMPLETED = 1;
    private static final int ORDER_STATUS_CANCELLED = 2;

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
        String orderNo = generateOrderNo();
        List<OrderItem> itemList = handleOrderItems(orderDto.getItems(), orderNo);

        BigDecimal totalAmount = itemList.stream().map(OrderItem::getProductPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(totalAmount);
        order.setStatus(ORDER_STATUS_PENDING);
        order.setReceiverName(orderDto.getReceiverName());
        order.setReceiverPhone(orderDto.getReceiverPhone());
        order.setReceiverAddress(orderDto.getReceiverAddress());
        orderMapper.insert(order);

        itemList.forEach(item -> item.setOrderId(order.getId()));
        orderItemMapper.insert(itemList);

        return convertToVO(order);
    }

    private List<OrderItem> handleOrderItems(List<OrderCreateDTO.OrderItemDTO> orderItems, String orderNo) {
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

        BatchStockDTO batchDTO = new BatchStockDTO();
        batchDTO.setOrderNo(orderNo);
        List<BatchStockDTO.StockItemDTO> stockItems = orderItems.stream().map(item -> {
            BatchStockDTO.StockItemDTO stockItem = new BatchStockDTO.StockItemDTO();
            stockItem.setProductId(item.getProductId());
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).toList();
        batchDTO.setItems(stockItems);

        Result<Boolean> stockResult = productClient.batchDeductStock(batchDTO);
        if (stockResult == null || stockResult.getData() == null || !stockResult.getData()) {
            log.error("批量扣减库存失败, 订单号: {}", orderNo);
            throw BusinessExceptionEnum.STOCK_NOT_ENOUGH.getException();
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

        BatchStockDTO batchDTO = new BatchStockDTO();
        batchDTO.setOrderNo(order.getOrderNo());
        List<BatchStockDTO.StockItemDTO> stockItems = items.stream().map(item -> {
            BatchStockDTO.StockItemDTO stockItem = new BatchStockDTO.StockItemDTO();
            stockItem.setProductId(item.getProductId());
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).toList();
        batchDTO.setItems(stockItems);

        productClient.batchRollbackStock(batchDTO);

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
