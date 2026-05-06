package com.lawyus.snackstore.order.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderQueryDTO {

    private Integer status;

    private Long userId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
