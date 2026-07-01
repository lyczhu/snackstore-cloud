package com.lawyus.snackstore.product.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchStockDTO {

    @Valid
    @NotEmpty(message = "商品列表不能为空")
    private List<StockDTO> items;

    private String orderNo;
}