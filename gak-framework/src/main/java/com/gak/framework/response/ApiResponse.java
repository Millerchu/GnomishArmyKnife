package com.gak.framework.response;

/**
 * 统一成功/失败响应体。
 *
 * @param code 业务码
 * @param message 提示信息
 * @param data 响应数据
 * @param <T> 数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    private static final String SUCCESS_CODE = "0";
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
