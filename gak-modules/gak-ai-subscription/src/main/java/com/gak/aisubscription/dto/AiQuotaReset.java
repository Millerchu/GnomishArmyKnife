package com.gak.aisubscription.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** 截至采集时仍可手动使用的重置机会，可合并同一到期时间的次数。 */
public record AiQuotaReset(@NotBlank @Size(max = 80) String label,
        @NotNull @Min(1) @Max(10000) Integer count, LocalDateTime expiresAt) {}
