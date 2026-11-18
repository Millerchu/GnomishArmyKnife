package com.gak.aisubscription.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.gak.aisubscription.enums.*;
/** 智订写入参数；不接受客户端用户归属。 */
public record SaveAiUsageRequest(@NotNull Long accountId, @NotNull AiUsageKind kind, @NotNull AiUsageMode mode, @NotBlank @Size(max=100) String periodKey, @NotBlank @Size(max=64) String zoneId, @NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotBlank @Size(max=64) String metric, @NotBlank @Size(max=32) String unit, @Size(max=128) String dimension, @NotNull @DecimalMin("0") @Digits(integer=18,fraction=6) BigDecimal quantity, @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal cost, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency) {}
