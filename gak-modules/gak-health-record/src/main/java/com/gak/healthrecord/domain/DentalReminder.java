package com.gak.healthrecord.domain;

import java.time.LocalDate;

/** 下次洁牙日期与用户提醒设置。 */
public record DentalReminder(Long ownerUserId, boolean enabled, int intervalMonths, int leadDays,
                             LocalDate nextDueDate) {
}
