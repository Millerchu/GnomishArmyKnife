package com.gak.framework.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        when(capture.capture(any(), any())).thenReturn("0123456789abcdef0123456789abcdef");
    }

    @Test
    void recordsUnhandledExceptionWithoutLeakingItsMessage() {
        IllegalStateException failure = new IllegalStateException("password=memo-secret");
        var response = handler.handleOther(failure);
        assertEquals("0123456789abcdef0123456789abcdef", response.getHeaders().getFirst("X-Trace-Id"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("服务器内部错误", response.getBody().message());
        verify(capture).capture(failure, SystemExceptionCapture.SOURCE_HTTP);
    }

    @Test
    void skipsBusinessValidationAndClientFailures() {
        assertNull(handler.handleBusiness(new BusinessException("BIZ", "请输入名称"))
                .getHeaders().getFirst("X-Trace-Id"));
        assertNull(handler.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(java.util.Set.of()))
                .getHeaders().getFirst("X-Trace-Id"));
        assertNull(handler.handleOther(new BindException(new Object(), "form")).getHeaders().getFirst("X-Trace-Id"));
        handler.handleOther(new MaxUploadSizeExceededException(1024));
        handler.handleOther(new ClientAbortException());
        assertNull(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以查看系统日志"))
                .getHeaders().getFirst("X-Trace-Id"));
        verify(capture, never()).capture(any(), any());
    }

    @Test
    void recordsFiveHundredStatusAndKeepsResponseWhenCaptureFails() {
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "磁盘写入失败");
        var response = handler.handleResponseStatus(failure);
        assertEquals("0123456789abcdef0123456789abcdef", response.getHeaders().getFirst("X-Trace-Id"));
        assertEquals("磁盘写入失败", response.getBody().message());
        verify(capture).capture(eq(failure), eq(SystemExceptionCapture.SOURCE_HTTP));

        doThrow(new IllegalStateException("log down")).when(capture).capture(any(), any());
        var fallback = handler.handleOther(new IllegalStateException("password=memo-secret"));
        assertEquals(500, fallback.getStatusCode().value());
        assertEquals("服务器内部错误", fallback.getBody().message());
        assertNull(fallback.getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void committedSseResponseKeepsCaptureButDoesNotAddHeader() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/messages/stream");
        var servletResponse = new org.springframework.mock.web.MockHttpServletResponse();
        servletResponse.setCommitted(true);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, servletResponse));
        try {
            var failure = new IllegalStateException("synthetic");
            assertNull(handler.handleOther(failure).getHeaders().getFirst("X-Trace-Id"));
            verify(capture).capture(failure, SystemExceptionCapture.SOURCE_HTTP);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void absentCaptureDoesNotAddHeader() {
        ObjectProvider<SystemExceptionCapture> provider = mock(ObjectProvider.class);
        assertNull(new GlobalExceptionHandler(provider).handleOther(new IllegalStateException())
                .getHeaders().getFirst("X-Trace-Id"));
    }

    private static class ClientAbortException extends RuntimeException {
        private ClientAbortException() {
            super("client closed");
        }
    }
}
