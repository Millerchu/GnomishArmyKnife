package com.gak.aisubscription.sync;

import java.math.BigDecimal;
import java.time.LocalDate;
/** 官方 UTC 日费用快照；不是 Token 数量，也不是现金付款。 */
public record AiOfficialCost(LocalDate startDate, LocalDate endDate, String currency, BigDecimal amount) { }
