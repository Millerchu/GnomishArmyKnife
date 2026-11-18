package com.gak.aisubscription.vo;
import java.math.BigDecimal;
import java.time.LocalDate;
/** 不返回账单账号等非关联所需信息。 */
public record AiBillLinkVO(String billId, String currency, BigDecimal amount, LocalDate billDate,
        boolean generated, boolean available) {}
