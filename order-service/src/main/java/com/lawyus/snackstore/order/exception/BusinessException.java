package com.lawyus.snackstore.order.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 自定义业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     * -- GETTER --
     *  获取错误码
     *
     * @return 错误码

     */
    private Integer code;

    /**
     * 错误信息
     */
    private String message;

    /**
     * 设置错误码
     *
     * @param code 错误码
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 设置错误信息
     *
     * @param message 错误信息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public BusinessException() {
        super();
    }

    public BusinessException(String message) {
        super(message);
        this.message = message;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
}