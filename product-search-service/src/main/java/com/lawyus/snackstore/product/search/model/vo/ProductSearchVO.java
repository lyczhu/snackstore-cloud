package com.lawyus.snackstore.product.search.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private Integer categorySort;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private String description;

    private Integer status;

    private String highlightedName;

    private String highlightedDescription;
}
