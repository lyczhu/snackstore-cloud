package com.lawyus.snackstore.product.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryDTO {

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Integer sort;

    private Integer status;
}
