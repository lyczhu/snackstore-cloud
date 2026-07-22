package com.lawyus.snackstore.statistics.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CategorySalesVO {

    private Long categoryId;

    private String categoryName;

    private Long quantity;

    private BigDecimal amount;
}
