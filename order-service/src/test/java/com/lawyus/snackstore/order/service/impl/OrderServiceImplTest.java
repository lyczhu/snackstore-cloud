package com.lawyus.snackstore.order.service.impl;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.exception.BusinessException;
import com.lawyus.snackstore.order.feign.product.BatchStockDTO;
import com.lawyus.snackstore.order.feign.product.ProductClient;
import com.lawyus.snackstore.order.feign.product.ProductVO;
import com.lawyus.snackstore.order.mapper.OrderItemMapper;
import com.lawyus.snackstore.order.mapper.OrderMapper;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.entity.OrderItem;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private OrderCreateDTO buildOrderCreateDTO() {
        OrderCreateDTO dto = new OrderCreateDTO();
        OrderCreateDTO.OrderItemDTO item1 = new OrderCreateDTO.OrderItemDTO();
        item1.setProductId(1L);
        item1.setQuantity(2);
        OrderCreateDTO.OrderItemDTO item2 = new OrderCreateDTO.OrderItemDTO();
        item2.setProductId(2L);
        item2.setQuantity(3);
        dto.setItems(List.of(item1, item2));
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setReceiverAddress("北京市朝阳区");
        return dto;
    }

    private List<ProductVO> buildProductVOList() {
        ProductVO vo1 = new ProductVO();
        vo1.setId(1L);
        vo1.setName("薯片");
        vo1.setCoverImage("img1.jpg");
        vo1.setPrice(new BigDecimal("9.99"));
        vo1.setStatus(1);
        ProductVO vo2 = new ProductVO();
        vo2.setId(2L);
        vo2.setName("可乐");
        vo2.setCoverImage("img2.jpg");
        vo2.setPrice(new BigDecimal("5.00"));
        vo2.setStatus(1);
        return List.of(vo1, vo2);
    }

    @Test
    void shouldCreateOrderWithBatchDeduction() {
        OrderCreateDTO orderDto = buildOrderCreateDTO();
        List<ProductVO> productVOList = buildProductVOList();

        when(productClient.getProductsByIds(any())).thenReturn(Result.success(productVOList));
        when(productClient.batchDeductStock(any(BatchStockDTO.class))).thenReturn(Result.success(true));

        orderService.createOrder(1L, orderDto);

        verify(productClient, times(1)).batchDeductStock(any(BatchStockDTO.class));
        verify(productClient, never()).deductStock(anyLong(), anyInt());
        verify(orderMapper, times(1)).insert(any(Order.class));
        verify(orderItemMapper, times(1)).insert(any(List.class));
    }

    @Test
    void shouldFailCreateOrderWhenBatchDeductionFails() {
        OrderCreateDTO orderDto = buildOrderCreateDTO();
        List<ProductVO> productVOList = buildProductVOList();

        when(productClient.getProductsByIds(any())).thenReturn(Result.success(productVOList));
        when(productClient.batchDeductStock(any(BatchStockDTO.class))).thenReturn(Result.failed(3003, "库存不足"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(1L, orderDto));
        assertTrue(exception.getMessage().contains("库存不足"));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldFailCreateOrderWhenProductOffShelf() {
        OrderCreateDTO orderDto = buildOrderCreateDTO();
        ProductVO vo1 = new ProductVO();
        vo1.setId(1L);
        vo1.setName("薯片");
        vo1.setStatus(0);
        List<ProductVO> productVOList = List.of(vo1);

        when(productClient.getProductsByIds(any())).thenReturn(Result.success(productVOList));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(1L, orderDto));
        assertTrue(exception.getMessage().contains("下架"));
        verify(productClient, never()).batchDeductStock(any(BatchStockDTO.class));
    }

    @Test
    void shouldCancelOrderWithBatchRollback() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setOrderNo("20260629120000001");
        order.setStatus(0);

        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setQuantity(2);

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(productClient.batchRollbackStock(any(BatchStockDTO.class))).thenReturn(Result.success(true));

        orderService.cancelOrder(1L, 1L);

        verify(productClient, times(1)).batchRollbackStock(any(BatchStockDTO.class));
        verify(productClient, never()).rollbackStock(anyLong(), anyInt());
        verify(orderMapper, times(1)).updateById(any(Order.class));
    }

    @Test
    void shouldFailCancelOrderWhenNotPending() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus(1);

        when(orderMapper.selectOne(any())).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, 1L));
        assertTrue(exception.getMessage().contains("取消"));
        verify(productClient, never()).batchRollbackStock(any(BatchStockDTO.class));
    }
}