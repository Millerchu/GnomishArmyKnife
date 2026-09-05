package com.gak.message.controller;

import com.gak.framework.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 消息枚举、ID 和 JSON 格式错误属于用户入参错误，避免落入通用 500 响应。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MessageController.class)
public class MessageExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("400", "消息参数格式错误，请检查分类、重要级别、接收范围和用户标识"));
    }
}
