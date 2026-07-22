package com.lawyus.snackstore.order.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class OrderTrendVO {

    private LocalDate date;

    private Long orderCount;

    private BigDecimal orderAmount;
}
