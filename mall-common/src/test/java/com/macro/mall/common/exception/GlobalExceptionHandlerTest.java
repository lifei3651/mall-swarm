package com.macro.mall.common.exception;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void authenticationAndAuthorizationUseRealHttpStatusCodes() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                handler.handle(new ApiException(ResultCode.UNAUTHORIZED)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                handler.handle(new ApiException(ResultCode.FORBIDDEN)).getStatusCode());
    }

    @Test
    void malformedRequestAndWrongMethodUseSafeStableResponses() {
        ResponseEntity<CommonResult<?>> malformed = handler.handleMalformedRequest(
                new HttpMessageNotReadableException("/private/path password=secret"));
        ResponseEntity<CommonResult<?>> method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("TRACE"));

        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertFalse(malformed.getBody().getMessage().contains("private"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, method.getStatusCode());
    }

    @Test
    void unexpectedFailureReturns500WithoutInternalMessage() {
        ResponseEntity<CommonResult<?>> response = handler.handleException(
                new IllegalStateException("jdbc:mysql://db password=secret"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("系统内部错误，请稍后重试", response.getBody().getMessage());
    }
}
