package com.lawyus.snackstore.order.feign.product;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchStockDTO {

    private List<StockItemDTO> items;

    private String orderNo;

    @Getter
    @Setter
    public static class StockItemDTO {

        private Long productId;

        private Integer quantity;
    }
}