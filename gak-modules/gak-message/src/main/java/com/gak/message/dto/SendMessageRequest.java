package com.gak.message.dto;
import com.gak.framework.message.*;
import jakarta.validation.constraints.*;
import java.util.List;
/** 管理员发送入参，发送人和来源只能由服务端设置。 */
public record SendMessageRequest(
        @NotBlank @Size(max = 160) String idempotencyKey,
        @NotNull MessageAudience audience,
        @NotNull List<@NotNull Long> userIds,
        @NotNull MessageCategory category, @NotNull MessagePriority priority,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 10000) String body) {}
