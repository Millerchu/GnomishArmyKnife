package com.gak.aisubscription.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
/** 月预算只约束同币种确认成本。 */
public record AiBudgetRequest(@NotBlank @Pattern(regexp="\\d{4}-\\d{2}") String month,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotNull @DecimalMin("0.01") @DecimalMax("1.00") @Digits(integer=1,fraction=2) BigDecimal threshold) {}
