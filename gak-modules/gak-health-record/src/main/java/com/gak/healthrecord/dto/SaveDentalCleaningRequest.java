package com.gak.healthrecord.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 洁牙记录表单。 */
public record SaveDentalCleaningRequest(
        @NotNull LocalDate cleaningDate,
        @NotBlank @Size(max = 64) String hospitalName,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 255) String note) {
}
