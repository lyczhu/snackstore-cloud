package com.lawyus.snackstore.product.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductDTO {

    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String coverImage;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private Integer stock;

    private String description;

    private String detail;

    private Integer status;
}
