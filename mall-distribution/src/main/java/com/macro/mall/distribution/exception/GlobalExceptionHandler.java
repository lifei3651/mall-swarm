package com.macro.mall.distribution.exception;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * 处理业务异常
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResult<Void>> handleApiException(ApiException e) {
        String message = e.getMessage();
        // 未登录相关异常返回 401
        if (message != null && message.contains("请先登录")) {
            LOGGER.warn("未登录访问: {}", message);
            CommonResult<Void> result = CommonResult.failed("请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        // 其他业务异常返回 400
        LOGGER.warn("业务异常: {}", message);
        CommonResult<Void> result = CommonResult.failed(message);
        return ResponseEntity.badRequest().body(result);
    }
}
