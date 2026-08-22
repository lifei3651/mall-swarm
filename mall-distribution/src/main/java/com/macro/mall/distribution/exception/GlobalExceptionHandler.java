package com.macro.mall.distribution.exception;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 分销模块全局异常处理器
 * 处理未登录等异常，返回正确的 HTTP 状态码
 */
@RestControllerAdvice(basePackages = "com.macro.mall.distribution")
@Order(-1) // 优先于公共异常处理器
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        LOGGER.warn("上传图片超过5MB限制");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(CommonResult.failed("单张图片不能超过5MB，请压缩后重试"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResult<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        LOGGER.warn("请求体格式错误: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(CommonResult.validateFailed("请求内容格式不正确"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        LOGGER.warn("请求方式不正确: method={}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(CommonResult.failed("请求方式不正确"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.BAD_REQUEST : status;
        String message = safeStatus == HttpStatus.TOO_MANY_REQUESTS
                ? "请求过于频繁，请稍后重试"
                : safeStatus == HttpStatus.METHOD_NOT_ALLOWED ? "请求方式不正确" : "请求未能完成";
        LOGGER.warn("受控HTTP异常: status={}, type={}", safeStatus.value(), e.getClass().getSimpleName());
        return ResponseEntity.status(safeStatus).body(CommonResult.failed(message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<CommonResult<Void>> handleValidation(Exception e) {
        FieldError fieldError = e instanceof MethodArgumentNotValidException valid
                ? valid.getBindingResult().getFieldError()
                : ((BindException) e).getBindingResult().getFieldError();
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? "请求参数不正确" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResult<Void>> handleApiException(ApiException e) {
        String message = e.getMessage();
        // 未登录相关异常返回 401
        if (e.getErrorCode() != null && e.getErrorCode().getCode() == ResultCode.UNAUTHORIZED.getCode()) {
            LOGGER.warn("未登录访问: {}", message);
            CommonResult<Void> result = CommonResult.failed("请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        // 其他业务异常返回 400
        LOGGER.warn("业务异常: {}", message);
        CommonResult<Void> result = CommonResult.failed(message);
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<Void>> handleUnexpectedException(Exception e) {
        LOGGER.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResult.failed("系统内部错误，请稍后重试"));
    }
}
