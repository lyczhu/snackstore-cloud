package com.lawyus.snackstore.order.feign.product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductFeignDetailVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private String description;

    private String detail;

    private Integer status;
}
