package com.lawyus.snackstore.product.service.impl;

import com.lawyus.snackstore.product.exception.BusinessException;
import com.lawyus.snackstore.product.mapper.ProductMapper;
import com.lawyus.snackstore.product.model.dto.BatchStockDTO;
import com.lawyus.snackstore.product.model.dto.StockDTO;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent.ChangeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductServiceImpl productService;

    private BatchStockDTO buildBatchDTO(List<StockDTO> items) {
        BatchStockDTO dto = new BatchStockDTO();
        dto.setItems(items);
        dto.setOrderNo("20260629120000001");
        return dto;
    }

    private StockDTO buildStockItem(Long productId, Integer quantity) {
        StockDTO item = new StockDTO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void shouldBatchDeductAllSuccessfully() {
        StockDTO item1 = buildStockItem(1L, 2);
        StockDTO item2 = buildStockItem(2L, 3);
        StockDTO item3 = buildStockItem(3L, 1);
        BatchStockDTO batchDTO = buildBatchDTO(List.of(item1, item2, item3));

        when(productMapper.update(isNull(), any())).thenReturn(1);

        boolean result = productService.batchDeductStock(batchDTO);

        assertTrue(result);
        verify(productMapper, times(3)).update(isNull(), any());
        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher, times(3)).publishEvent(captor.capture());
        List<ProductChangedEvent> events = captor.getAllValues();
        assertEquals(3, events.size());
        assertEquals(ChangeType.STOCK_CHANGED, events.getFirst().type());
    }

    @Test
    void shouldBatchDeductFailOnFirstItem() {
        StockDTO item1 = buildStockItem(1L, 100);
        BatchStockDTO batchDTO = buildBatchDTO(List.of(item1));

        when(productMapper.update(isNull(), any())).thenReturn(0);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> productService.batchDeductStock(batchDTO));
        assertTrue(exception.getMessage().contains("库存不足"));
        verify(productMapper, times(1)).update(isNull(), any());
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void shouldBatchDeductFailOnLastItem() {
        StockDTO item1 = buildStockItem(1L, 2);
        StockDTO item2 = buildStockItem(2L, 3);
        StockDTO item3 = buildStockItem(3L, 100);
        BatchStockDTO batchDTO = buildBatchDTO(List.of(item1, item2, item3));

        when(productMapper.update(isNull(), any()))
                .thenReturn(1)
                .thenReturn(1)
                .thenReturn(0);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> productService.batchDeductStock(batchDTO));
        assertTrue(exception.getMessage().contains("库存不足"));
        verify(productMapper, times(3)).update(isNull(), any());
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void shouldBatchRollbackAllSuccessfully() {
        StockDTO item1 = buildStockItem(1L, 2);
        StockDTO item2 = buildStockItem(2L, 3);
        BatchStockDTO batchDTO = buildBatchDTO(List.of(item1, item2));

        when(productMapper.update(isNull(), any())).thenReturn(1);

        boolean result = productService.batchRollbackStock(batchDTO);

        assertTrue(result);
        verify(productMapper, times(2)).update(isNull(), any());
        verify(eventPublisher, times(2)).publishEvent(any(ProductChangedEvent.class));
    }

    @Test
    void shouldBatchDeductPublishEventWithCorrectProductIds() {
        StockDTO item1 = buildStockItem(10L, 1);
        StockDTO item2 = buildStockItem(20L, 2);
        BatchStockDTO batchDTO = buildBatchDTO(List.of(item1, item2));

        when(productMapper.update(isNull(), any())).thenReturn(1);

        productService.batchDeductStock(batchDTO);

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<ProductChangedEvent> events = captor.getAllValues();
        assertEquals(10L, events.get(0).productId());
        assertEquals(20L, events.get(1).productId());
    }
}