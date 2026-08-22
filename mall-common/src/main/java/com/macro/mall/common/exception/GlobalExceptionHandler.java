package com.macro.mall.common.exception;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理
 * Created by macro on 2020/2/27.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<CommonResult<?>> handle(ApiException e) {
        // 记录业务异常日志（WARN级别）
        LOGGER.warn("业务异常: code={}, message={}",
                e.getErrorCode() != null ? e.getErrorCode().getCode() : "N/A",
                e.getMessage());
        if (e.getErrorCode() != null) {
            HttpStatus status = e.getErrorCode().getCode() == ResultCode.UNAUTHORIZED.getCode()
                    ? HttpStatus.UNAUTHORIZED
                    : e.getErrorCode().getCode() == ResultCode.FORBIDDEN.getCode()
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(CommonResult.failed(e.getErrorCode()));
        }
        return ResponseEntity.badRequest().body(CommonResult.failed(e.getMessage()));
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult<?>> handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }

    @ExceptionHandler(value = BindException.class)
    public ResponseEntity<CommonResult<?>> handleValidException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<?>> handleMalformedRequest(Exception e) {
        LOGGER.warn("请求参数格式错误: type={}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(CommonResult.validateFailed("请求内容格式不正确"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResult<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        LOGGER.warn("请求方式不正确: method={}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(CommonResult.failed("请求方式不正确"));
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<CommonResult<?>> handleException(Exception e) {
        LOGGER.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResult.failed("系统内部错误，请稍后重试"));
    }
}
