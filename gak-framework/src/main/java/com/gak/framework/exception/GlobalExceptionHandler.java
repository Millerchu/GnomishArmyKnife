package com.gak.framework.exception;

import com.gak.framework.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理。业务和校验异常只返回给调用方；未处理的系统异常才尝试采集。
 * 采集失败或采集器缺失时，仍返回原来的状态和文案。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectProvider<SystemExceptionCapture> captures;

    public GlobalExceptionHandler(ObjectProvider<SystemExceptionCapture> captures) {
        this.captures = captures;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数不合法";
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(String.valueOf(HttpStatus.BAD_REQUEST.value()), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(String.valueOf(HttpStatus.BAD_REQUEST.value()), ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            captureIfUnhandled(ex);
        }
        String message = ex.getReason() != null ? ex.getReason() : "请求处理失败";
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.failure(String.valueOf(status), message));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(String.valueOf(HttpStatus.NOT_FOUND.value()), "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        if (!isExpectedClientException(ex)) {
            captureIfUnhandled(ex);
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "服务器内部错误"));
    }

    /**
     * 这些异常属于调用方入参、认证结果或连接中断。部分类型目前仍由通用分支返回 500，
     * 这里只跳过采集，不改变既有状态码。
     */
    private boolean isExpectedClientException(Throwable exception) {
        return exception instanceof BusinessException
                || exception instanceof MethodArgumentNotValidException
                || exception instanceof ConstraintViolationException
                || exception instanceof BindException
                || exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof MissingServletRequestParameterException
                || exception instanceof MissingServletRequestPartException
                || exception instanceof HttpRequestMethodNotSupportedException
                || exception instanceof HttpMediaTypeNotSupportedException
                || exception instanceof HttpMediaTypeNotAcceptableException
                || exception instanceof NoHandlerFoundException
                || exception instanceof MaxUploadSizeExceededException
                || isClientDisconnect(exception);
    }

    private boolean isClientDisconnect(Throwable exception) {
        String name = exception.getClass().getSimpleName();
        return "ClientAbortException".equals(name) || "AsyncRequestNotUsableException".equals(name);
    }

    private void captureIfUnhandled(Throwable exception) {
        SystemExceptionCapture capture = captures.getIfAvailable();
        if (capture == null) {
            return;
        }
        try {
            capture.capture(exception, SystemExceptionCapture.SOURCE_HTTP);
        } catch (Throwable ignored) {
            // 采集器若违反约定抛错，也不能替换已经决定的响应。
        }
    }
}
