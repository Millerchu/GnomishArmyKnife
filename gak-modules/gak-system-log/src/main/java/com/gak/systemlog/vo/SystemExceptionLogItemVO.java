package com.gak.systemlog.vo;

import java.time.LocalDateTime;

/**
 * 列表项。故意不包含堆栈。
 */
public record SystemExceptionLogItemVO(
        Long id,
        LocalDateTime occurredAt,
        String traceId,
        String source,
        String exceptionType,
        String summary,
        String httpMethod,
        String requestPath
) {
}
