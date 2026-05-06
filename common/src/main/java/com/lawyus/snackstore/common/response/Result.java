package com.lawyus.snackstore.common.response;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回结果类
 *
 * @param <T> 返回数据类型
 */
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 默认构造函数
     */
    public Result() {
    }

    /**
     * 构造函数
     *
     * @param code    响应码
     * @param message 响应消息
     * @param data    响应数据
     */
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 获取响应码
     *
     * @return 响应码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 设置响应码
     *
     * @param code 响应码
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 获取响应消息
     *
     * @return 响应消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置响应消息
     *
     * @param message 响应消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取响应数据
     *
     * @return 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据
     *
     * @param data 响应数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 成功返回结果
     *
     * @param data 获取的数据
     * @param <T>  数据类型
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功返回结果
     *
     * @param data    获取的数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return Result
     */
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败返回结果
     *
     * @param errorCode 错误码
     * @param <T>       数据类型
     * @return Result
     */
    public static <T> Result<T> failed(ResultCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败返回结果
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param <T>       数据类型
     * @return Result
     */
    public static <T> Result<T> failed(ResultCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败返回结果
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return Result
     */
    public static <T> Result<T> failed(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 参数验证失败返回结果
     *
     * @param <T> 数据类型
     * @return Result
     */
    public static <T> Result<T> validateFailed() {
        return failed(ResultCode.VALIDATE_FAILED);
    }

    /**
     * 参数验证失败返回结果
     *
     * @param message 提示信息
     * @param <T>     数据类型
     * @return Result
     */
    public static <T> Result<T> validateFailed(String message) {
        return failed(ResultCode.VALIDATE_FAILED, message);
    }

    /**
     * 未登录返回结果
     *
     * @param <T> 数据类型
     * @return Result
     */
    public static <T> Result<T> unauthorized() {
        return failed(ResultCode.UNAUTHORIZED);
    }

    /**
     * 未授权返回结果
     *
     * @param <T> 数据类型
     * @return Result
     */
    public static <T> Result<T> forbidden() {
        return failed(ResultCode.FORBIDDEN);
    }
}