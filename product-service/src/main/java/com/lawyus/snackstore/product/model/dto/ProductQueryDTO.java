package com.lawyus.snackstore.product.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductQueryDTO {

    private Long categoryId;

    private String keyword;

    private Integer status;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
