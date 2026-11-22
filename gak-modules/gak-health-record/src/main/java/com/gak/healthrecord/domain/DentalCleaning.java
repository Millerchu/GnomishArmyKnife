package com.gak.healthrecord.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 洁牙记录的持久化字段。 */
public record DentalCleaning(Long id, Long ownerUserId, LocalDate cleaningDate, String hospitalName,
                             BigDecimal amount, String note) {
}
