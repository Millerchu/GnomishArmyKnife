package com.gak.healthrecord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 用户的洁牙提醒周期及提前量。 */
public record SaveDentalReminderRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(60) Integer intervalMonths,
        @NotNull @Min(0) @Max(365) Integer leadDays) {
}
