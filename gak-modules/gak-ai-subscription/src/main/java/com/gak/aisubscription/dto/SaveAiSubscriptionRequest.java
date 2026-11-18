package com.gak.aisubscription.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.gak.aisubscription.enums.*;
/** 智订写入参数；不接受客户端用户归属。 */
public record SaveAiSubscriptionRequest(@NotNull Long accountId, @NotBlank @Size(max=100) String planName, @NotNull LocalDate startDate, @NotNull LocalDate endDate, LocalDate nextBillingDate, @NotNull @Min(0) @Max(120) Integer cycleMonths, @NotNull @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal amount, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotNull Boolean autoRenew, LocalDate trialEndDate, @NotNull @Min(0) @Max(365) Integer reminderDays) {}
