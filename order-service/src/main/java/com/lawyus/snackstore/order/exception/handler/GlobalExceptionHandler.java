package com.lawyus.snackstore.order.exception.handler;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.order.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义业务异常
     *
     * @param e 业务异常
     * @return Result
     */
    @ExceptionHandler(value = BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return Result.failed(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数验证异常(MethodArgumentNotValidException)
     *
     * @param e 验证异常
     * @return Result
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = firstValidationMessage(bindingResult);
        log.warn("参数验证失败: {}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理参数验证异常(BindException)
     *
     * @param e 验证异常
     * @return Result
     */
    @ExceptionHandler(value = BindException.class)
    public Result<Object> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = firstValidationMessage(bindingResult);
        log.warn("参数绑定失败: {}", message);
        return Result.validateFailed(message);
    }

    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElseGet(() -> bindingResult.getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("参数校验失败"));
    }

    /**
     * 处理参数验证异常(普通字段验证)
     *
     * @param e 验证异常
     * @return Result
     */
    @ExceptionHandler(value = IllegalArgumentException.class)
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数非法: {}", e.getMessage());
        return Result.validateFailed(e.getMessage());
    }

    /**
     * 处理缺少必填请求头(X-User-Id 缺失即 403)
     *
     * @param e 缺失请求头异常
     * @return Result
     */
    @ExceptionHandler(value = MissingRequestHeaderException.class)
    public Result<Object> handleMissingRequestHeader(MissingRequestHeaderException e) {
        if (AuthConstants.HEADER_USER_ID.equals(e.getHeaderName())) {
            log.warn("缺少身份标识请求头: {}", e.getMessage());
            return Result.forbidden();
        }
        log.warn("缺少请求头: {}", e.getMessage());
        return Result.validateFailed(e.getMessage());
    }

    /**
     * 处理安全校验失败(归属校验等)
     *
     * @param e 安全异常
     * @return Result
     */
    @ExceptionHandler(value = SecurityException.class)
    public Result<Object> handleSecurityException(SecurityException e) {
        log.warn("安全校验失败: {}", e.getMessage());
        return Result.forbidden();
    }

    /**
     * 处理系统异常
     *
     * @param e 系统异常
     * @return Result
     */
    @ExceptionHandler(value = Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.failed(ResultCode.INTERNAL_SERVER_ERROR.getCode(), ResultCode.INTERNAL_SERVER_ERROR.getMessage());
    }
}