package com.gak.systemlog.service;

/**
 * 可以落库的异常快照。字段在构造前已经完成脱敏和截断。
 */
public record SanitizedSystemException(
        String exceptionType,
        String summary,
        String stackTrace,
        boolean stackTruncated,
        String httpMethod,
        String requestPath
) {
}
