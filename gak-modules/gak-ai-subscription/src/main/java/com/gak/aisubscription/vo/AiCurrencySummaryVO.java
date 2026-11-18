package com.gak.aisubscription.vo;
import java.math.BigDecimal;
/** 原币独立金额，不混加不同币种。 */
public record AiCurrencySummaryVO(String currency, BigDecimal confirmedCost, BigDecimal cashExpense, BigDecimal monthlyCommitment, BigDecimal estimatedCost) {}
