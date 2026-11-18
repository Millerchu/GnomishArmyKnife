package com.gak.aisubscription.vo;
import java.math.BigDecimal;
/** 原币预算与当前已确认成本，不把待核对金额当作零成本。 */
public record AiBudgetVO(String month, String currency, BigDecimal amount, BigDecimal threshold,
        BigDecimal confirmedCost, boolean complete) {}
