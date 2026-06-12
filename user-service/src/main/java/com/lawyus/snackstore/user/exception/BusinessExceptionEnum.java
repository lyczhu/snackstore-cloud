package com.lawyus.snackstore.user.exception;

import com.lawyus.snackstore.common.response.ResultCode;

public enum BusinessExceptionEnum {

    USER_NOT_FOUND(ResultCode.USER_NOT_FOUND),
    USER_DISABLED(ResultCode.USER_DISABLED),
    USER_ALREADY_DISABLED(ResultCode.USER_ALREADY_DISABLED),
    USER_ALREADY_ENABLED(ResultCode.USER_ALREADY_ENABLED),
    PASSWORD_ERROR(ResultCode.PASSWORD_ERROR),
    TOKEN_INVALID(ResultCode.TOKEN_INVALID),
    TOKEN_EXPIRED(ResultCode.TOKEN_EXPIRED),
    PHONE_ALREADY_EXISTS(ResultCode.PHONE_ALREADY_EXISTS),
    SMS_CODE_ERROR(ResultCode.SMS_CODE_ERROR),
    SMS_CODE_EXPIRED(ResultCode.SMS_CODE_EXPIRED),

    PRODUCT_NOT_FOUND(ResultCode.PRODUCT_NOT_FOUND),
    PRODUCT_OFF_SHELF(ResultCode.PRODUCT_OFF_SHELF),
    STOCK_NOT_ENOUGH(ResultCode.STOCK_NOT_ENOUGH),
    CATEGORY_NOT_FOUND(ResultCode.CATEGORY_NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(ResultCode.CATEGORY_ALREADY_EXISTS),
    SNAPSHOT_NOT_FOUND(ResultCode.SNAPSHOT_NOT_FOUND),

    ORDER_NOT_FOUND(ResultCode.ORDER_NOT_FOUND),
    ORDER_STATUS_ERROR(ResultCode.ORDER_STATUS_ERROR),
    ORDER_CREATE_FAILED(ResultCode.ORDER_CREATE_FAILED),
    ORDER_CANNOT_CANCEL(ResultCode.ORDER_CANNOT_CANCEL),
    ORDER_CANNOT_PAY(ResultCode.ORDER_CANNOT_PAY),

    DATA_NOT_FOUND(ResultCode.DATA_NOT_FOUND),
    DATA_ALREADY_EXISTS(ResultCode.DATA_ALREADY_EXISTS),

    SYSTEM_ERROR(ResultCode.INTERNAL_SERVER_ERROR);

    private final ResultCode resultCode;

    BusinessExceptionEnum(ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    public Integer getCode() {
        return resultCode.getCode();
    }

    public String getMessage() {
        return resultCode.getMessage();
    }

    public BusinessException getException() {
        return new BusinessException(this.resultCode.getCode(), this.resultCode.getMessage());
    }

    public BusinessException getException(String message) {
        return new BusinessException(this.resultCode.getCode(), message);
    }
}
