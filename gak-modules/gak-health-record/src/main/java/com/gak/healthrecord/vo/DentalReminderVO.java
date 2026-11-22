package com.gak.healthrecord.vo;

import java.time.LocalDate;

/** 提醒设置及下次发送日期。 */
public record DentalReminderVO(boolean enabled, int intervalMonths, int leadDays, LocalDate nextDueDate,
                               LocalDate nextReminderDate) {
}
