package com.macro.mall.distribution.exception;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.api.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unauthorizedUsesErrorCodeInsteadOfChineseMessageMatching() {
        ResponseEntity<CommonResult<Void>> response = handler.handleApiException(
                new ApiException(ResultCode.UNAUTHORIZED, "会话已失效"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("请先登录", response.getBody().getMessage());
    }

    @Test
    void malformedBodyAndWrongMethodReturnSafeMessages() {
        ResponseEntity<CommonResult<Void>> malformed = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("/private/path/Parser.java"));
        ResponseEntity<CommonResult<Void>> method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("TRACE"));

        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertEquals("请求内容格式不正确", malformed.getBody().getMessage());
        assertFalse(malformed.getBody().getMessage().contains("private"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, method.getStatusCode());
        assertEquals("请求方式不正确", method.getBody().getMessage());
    }

    @Test
    void responseStatusReasonIsNotReturned() {
        ResponseEntity<CommonResult<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "/secret/internal/reason"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("请求过于频繁，请稍后重试", response.getBody().getMessage());
    }
}
