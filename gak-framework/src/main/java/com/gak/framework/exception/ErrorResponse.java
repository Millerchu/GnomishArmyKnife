package com.gak.framework.exception;

/**
 * 统一错误返回体。
 */
public record ErrorResponse(String code, String message) {
}
