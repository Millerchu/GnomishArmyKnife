package com.gak.aisubscription.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Grok 的来源或 Bot 明细，百分比以厂商展示为准。 */
public record AiQuotaBreakdown(@NotBlank @Pattern(regexp = "SOURCE|BOT") String group,
        @NotBlank @Size(max = 80) String label,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal percent) {}
