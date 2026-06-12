package com.lawyus.snackstore.product.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_product_snapshot")
public class ProductSnapshot {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
