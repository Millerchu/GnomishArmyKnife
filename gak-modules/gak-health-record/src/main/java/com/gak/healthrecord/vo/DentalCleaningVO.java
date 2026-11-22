package com.gak.healthrecord.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 洁牙历史列表项。 */
public record DentalCleaningVO(String id, LocalDate cleaningDate, String hospitalName, BigDecimal amount,
                               String note) {
}
