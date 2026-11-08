package com.gak.framework.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/** 只有未处理的系统异常才会采集，采集失败不改变原来的响应。 */
class GlobalExceptionHandlerTest {

    private SystemExceptionCapture capture;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setup() {
        capture = mock(SystemExceptionCapture.class);
        ObjectProvider<SystemExceptionCapture> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(capture);
        handler = new GlobalExceptionHandler(provider);
    }

    @Test
    void recordsUnhandledExceptionWithoutLeakingItsMessage() {
        IllegalStateException failure = new IllegalStateException("password=memo-secret");
        var response = handler.handleOther(failure);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("服务器内部错误", response.getBody().message());
        verify(capture).capture(failure, SystemExceptionCapture.SOURCE_HTTP);
    }

    @Test
    void skipsBusinessValidationAndClientFailures() {
        handler.handleBusiness(new BusinessException("BIZ", "请输入名称"));
        handler.handleOther(new BindException(new Object(), "form"));
        handler.handleOther(new MaxUploadSizeExceededException(1024));
        handler.handleOther(new ClientAbortException());
        handler.handleResponseStatus(new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以查看系统日志"));
        verify(capture, never()).capture(any(), any());
    }

    @Test
    void recordsFiveHundredStatusAndKeepsResponseWhenCaptureFails() {
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "磁盘写入失败");
        var response = handler.handleResponseStatus(failure);
        assertEquals("磁盘写入失败", response.getBody().message());
        verify(capture).capture(eq(failure), eq(SystemExceptionCapture.SOURCE_HTTP));

        doThrow(new IllegalStateException("log down")).when(capture).capture(any(), any());
        var fallback = handler.handleOther(new IllegalStateException("password=memo-secret"));
        assertEquals(500, fallback.getStatusCode().value());
        assertEquals("服务器内部错误", fallback.getBody().message());
    }

    private static class ClientAbortException extends RuntimeException {
        private ClientAbortException() {
            super("client closed");
        }
    }
}
