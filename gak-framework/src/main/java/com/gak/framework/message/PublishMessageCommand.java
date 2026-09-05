package com.gak.framework.message;

import jakarta.validation.constraints.*;
import java.util.List;

/** 业务模块统一发送命令；幂等键由业务事件稳定标识生成。 */
public record PublishMessageCommand(
        @NotBlank @Size(max = 64) String source,
        @NotBlank @Size(max = 160) String idempotencyKey,
        Long senderId,
        @NotNull MessageAudience audience,
        @NotNull List<@NotNull Long> userIds,
        @NotNull MessageCategory category,
        @NotNull MessagePriority priority,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 10000) String body,
        MessageTarget target) {
}
