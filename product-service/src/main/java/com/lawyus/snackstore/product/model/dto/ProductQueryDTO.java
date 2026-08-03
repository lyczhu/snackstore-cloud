package com.lawyus.snackstore.product.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductQueryDTO {

    private Long categoryId;

    private String keyword;

    private Integer status;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;

    private String sortField;

    @Pattern(regexp = "^(asc|desc)$", message = "排序方式只能是asc或desc")
    private String sortOrder = "desc";
}
