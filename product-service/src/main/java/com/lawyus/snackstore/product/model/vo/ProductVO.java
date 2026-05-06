package com.lawyus.snackstore.product.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private String description;

    private Integer status;
}
