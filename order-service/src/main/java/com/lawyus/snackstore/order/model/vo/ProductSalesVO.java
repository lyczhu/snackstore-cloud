package com.lawyus.snackstore.order.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSalesVO {

    private Long productId;

    private String productName;

    private Long quantity;

    private BigDecimal amount;
}
