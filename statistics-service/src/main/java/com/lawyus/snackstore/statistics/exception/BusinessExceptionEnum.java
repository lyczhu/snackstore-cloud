package com.lawyus.snackstore.statistics.exception;

import com.lawyus.snackstore.common.response.ResultCode;

public enum BusinessExceptionEnum {

    STATISTICS_SERVICE_ERROR(ResultCode.STATISTICS_SERVICE_ERROR),
    ACCESS_FORBIDDEN(ResultCode.FORBIDDEN);

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
