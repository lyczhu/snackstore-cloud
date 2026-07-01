package com.lawyus.snackstore.product.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawyus.snackstore.product.model.dto.BatchStockDTO;
import com.lawyus.snackstore.product.model.dto.StockDTO;
import com.lawyus.snackstore.product.service.ProductService;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldBatchDeductStock() throws Exception {
        BatchStockDTO batchDTO = new BatchStockDTO();
        StockDTO item1 = new StockDTO();
        item1.setProductId(1L);
        item1.setQuantity(2);
        batchDTO.setItems(List.of(item1));
        batchDTO.setOrderNo("20260701120000001");

        when(productService.batchDeductStock(any(BatchStockDTO.class))).thenReturn(true);

        mockMvc.perform(post("/product/batchDeductStock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(productService, times(1)).batchDeductStock(any(BatchStockDTO.class));
    }

    @Test
    void shouldRejectBatchDeductEmptyItems() throws Exception {
        BatchStockDTO batchDTO = new BatchStockDTO();
        batchDTO.setItems(List.of());

        mockMvc.perform(post("/product/batchDeductStock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void shouldRejectBatchDeductNullQuantity() throws Exception {
        String json = "{\"items\":[{\"productId\":1}],\"orderNo\":\"20260701120000001\"}";

        mockMvc.perform(post("/product/batchDeductStock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
