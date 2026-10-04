package com.gak.healthrecord.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gak.healthrecord.dto.SaveHealthVisitRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** HTTP 入参金额范围、精度和可空兼容性。 */
class HealthVisitAmountValidationTest {
    @Test
    void shouldValidateAmountAtRequestBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            SaveHealthVisitRequest request = new SaveHealthVisitRequest();
            request.setHospitalName("测试医院");
            request.setVisitDate(LocalDate.of(2026, 10, 4));
            request.setChiefComplaint("复查");
            for (String value : new String[]{null, "0", "128.50", "9999999999.99"}) {
                request.setAmount(value == null ? null : new BigDecimal(value));
                assertTrue(validator.validate(request).isEmpty(), "应接受金额 " + value);
            }
            for (String value : new String[]{"-0.01", "1.001", "10000000000"}) {
                request.setAmount(new BigDecimal(value));
                assertTrue(validator.validate(request).stream()
                        .anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString())),
                        "应拒绝金额 " + value);
            }
        }
    }
}
