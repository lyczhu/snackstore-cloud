package com.lawyus.snackstore.order.constant;

/**
 * 订单状态常量。
 * 0=PENDING(待支付) 1=COMPLETED(已完成/已支付) 2=CANCELLED(已取消)
 */
public final class OrderStatusConstants {

    public static final int PENDING = 0;
    public static final int COMPLETED = 1;
    public static final int CANCELLED = 2;

    private OrderStatusConstants() {
    }
}
