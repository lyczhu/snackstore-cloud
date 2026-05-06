package com.lawyus.snackstore.product.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryVO {

    private Long id;

    private String name;

    private Integer sort;

    private Integer status;
}
