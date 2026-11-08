package com.gak.systemlog.controller;

import com.gak.framework.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 系统日志查询的参数校验与格式错误属于调用方问题，不采集为系统异常。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SystemExceptionLogController.class)
public class SystemExceptionLogExceptionHandler {

    private static final String QUERY_INVALID = "SYSTEM_LOG_QUERY_INVALID";

    /** 只转换分页范围约束；格式绑定失败及其他字段校验沿用原有错误码。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidValidation(MethodArgumentNotValidException exception) {
        boolean invalidPage = exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> !error.isBindingFailure()
                        && ("pageNo".equals(error.getField()) || "pageSize".equals(error.getField()))
                        && ("Min".equals(error.getCode()) || "Max".equals(error.getCode())));
        if (invalidPage) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(QUERY_INVALID, "分页参数不合法"));
        }
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数不合法";
        return ResponseEntity.badRequest().body(ApiResponse.failure("400", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidQuery(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("400", "系统日志查询参数格式错误"));
    }
}
