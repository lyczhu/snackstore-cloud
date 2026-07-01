package com.lawyus.snackstore.order.feign.product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductVO {

    private Long id;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private Integer status;
}
