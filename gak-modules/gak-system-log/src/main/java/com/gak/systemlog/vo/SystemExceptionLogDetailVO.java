package com.gak.systemlog.vo;

import java.time.LocalDateTime;

/**
 * 详情。堆栈已脱敏并限制长度，截断时 stackTruncated 为 true，正文以截断标记结尾。
 */
public record SystemExceptionLogDetailVO(
        Long id,
        LocalDateTime occurredAt,
        String traceId,
        String source,
        String exceptionType,
        String summary,
        String httpMethod,
        String requestPath,
        String stackTrace,
        boolean stackTruncated
) {
}
