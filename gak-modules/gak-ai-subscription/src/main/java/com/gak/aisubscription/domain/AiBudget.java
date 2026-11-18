package com.gak.aisubscription.domain;
import java.math.BigDecimal;
/** 用户某月某币种预算的存储映射。 */
public record AiBudget(Long ownerUserId, String budgetMonth, String currency, BigDecimal amount, BigDecimal threshold) {}
