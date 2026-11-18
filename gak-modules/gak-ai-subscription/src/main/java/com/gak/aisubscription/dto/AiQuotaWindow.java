package com.gak.aisubscription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 单个额度窗口，保留厂商的已用/剩余口径与重置提示。日期使用上海时间。 */
public record AiQuotaWindow(@NotBlank @Size(max = 80) String label,
        @NotBlank @Pattern(regexp = "USED|REMAINING") String reading,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal percent,
        LocalDateTime resetAt, @Size(max = 120) String resetHint, @Size(max = 80) String periodLabel,
        @Pattern(regexp = "[0-9]{1,24}") String remaining, @Size(max = 24) String unit,
        @Size(max = 24) List<@NotNull @Valid AiQuotaBreakdown> breakdown) {}
