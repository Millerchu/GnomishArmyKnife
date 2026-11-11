package com.gak.workbench.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.workbench.exception.WorkbenchPreferenceConflictException;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import com.gak.workbench.vo.WorkbenchPreferenceVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 只改变工作台输入和冲突响应，其他 BusinessException 仍走全局 400。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = WorkbenchPreferenceController.class)
public class WorkbenchPreferenceExceptionHandler {
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(415).body(ApiResponse.failure(WorkbenchPreferenceInvalidException.CODE,
                "工作台偏好请求必须使用 Content-Type: application/json"));
    }

    @ExceptionHandler(WorkbenchPreferenceConflictException.class)
    public ResponseEntity<ApiResponse<WorkbenchPreferenceVO>> conflict(WorkbenchPreferenceConflictException exception) {
        return ResponseEntity.status(409).body(new ApiResponse<>(
                WorkbenchPreferenceConflictException.CODE, exception.getMessage(), exception.getLatest()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception exception) {
        String message = "工作台偏好参数格式错误，请检查 version、schemaVersion 和 payload";
        if (exception instanceof MethodArgumentNotValidException validation
                && validation.getBindingResult().getFieldError() != null) {
            message = validation.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure(WorkbenchPreferenceInvalidException.CODE, message));
    }
}
