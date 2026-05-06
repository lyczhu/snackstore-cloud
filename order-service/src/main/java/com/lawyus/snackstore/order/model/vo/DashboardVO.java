package com.lawyus.snackstore.order.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardVO {

    private Long productCount;

    private Long orderCount;

    private Long todayOrderCount;

    private Long userCount;
}
