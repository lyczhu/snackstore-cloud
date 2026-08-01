package com.lawyus.snackstore.common.response;

public enum ResultCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    INTERNAL_SERVER_ERROR(500, "系统错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    FAILED(1000, "操作失败"),
    VALIDATE_FAILED(1001, "参数校验失败"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_ALREADY_EXISTS(1003, "数据已存在"),

    USER_NOT_FOUND(2001, "用户不存在"),
    USER_DISABLED(2002, "用户已被禁用"),
    USER_ALREADY_DISABLED(2003, "用户已处于禁用状态"),
    USER_ALREADY_ENABLED(2004, "用户已处于启用状态"),
    PASSWORD_ERROR(2005, "密码错误"),
    TOKEN_INVALID(2006, "token无效"),
    TOKEN_EXPIRED(2007, "token已过期"),
    PHONE_ALREADY_EXISTS(2008, "手机号已注册"),
    SMS_CODE_ERROR(2009, "验证码错误"),
    SMS_CODE_EXPIRED(2010, "验证码已过期"),

    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_OFF_SHELF(3002, "商品已下架"),
    STOCK_NOT_ENOUGH(3003, "库存不足"),
    CATEGORY_NOT_FOUND(3004, "分类不存在"),
    CATEGORY_ALREADY_EXISTS(3005, "分类已存在"),
    SNAPSHOT_NOT_FOUND(3006, "商品快照不存在"),
    PRODUCT_SEARCH_ERROR(3007, "商品搜索异常"),

    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_STATUS_ERROR(4002, "订单状态异常"),
    ORDER_CREATE_FAILED(4003, "订单创建失败"),
    ORDER_CANNOT_CANCEL(4004, "订单无法取消"),
    ORDER_CANNOT_PAY(4005, "订单无法支付"),
    ORDER_CANCEL_FAILED(4006, "订单取消失败"),

    STATISTICS_SERVICE_ERROR(5001, "统计服务异常"),

    RATE_LIMIT_EXCEEDED(6001, "请求频率超限"),
    SERVICE_DEGRADED(6002, "服务降级中，请稍后再试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
