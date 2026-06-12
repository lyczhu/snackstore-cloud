package com.lawyus.snackstore.product.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductSnapshotVO {

    private Long id;

    private Long productId;

    private Long categoryId;

    private String categoryName;

    private Integer categorySort;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private String description;

    private String detail;

    private Integer status;

    private LocalDateTime createdAt;
}
