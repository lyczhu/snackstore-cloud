package com.lawyus.snackstore.product.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSnapshotQueryDTO {

    private Long productId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 200, message = "每页条数不能超过200")
    private Integer pageSize = 10;
}
