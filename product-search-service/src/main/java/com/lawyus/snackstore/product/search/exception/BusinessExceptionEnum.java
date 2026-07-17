package com.lawyus.snackstore.product.search.exception;

import com.lawyus.snackstore.common.response.ResultCode;

public enum BusinessExceptionEnum {

    PRODUCT_SEARCH_ERROR(ResultCode.PRODUCT_SEARCH_ERROR),
    DATA_NOT_FOUND(ResultCode.DATA_NOT_FOUND),
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
