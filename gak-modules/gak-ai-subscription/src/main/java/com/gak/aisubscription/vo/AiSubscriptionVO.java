package com.gak.aisubscription.vo;
import java.time.*;
import java.math.BigDecimal;
/** 智订查询结果，不含凭据。 */
public record AiSubscriptionVO(Long id, Long accountId, String planName, LocalDate startDate, LocalDate endDate, LocalDate nextBillingDate, Integer cycleMonths, BigDecimal amount, String currency, Boolean autoRenew, LocalDate trialEndDate, Integer reminderDays, Boolean terminated, LocalDateTime recordedAt) {}
