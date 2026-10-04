package com.gak.growthrecord.service;

import com.gak.growthrecord.dto.SaveGrowthMeasurementRequest;
import com.gak.growthrecord.dto.SaveGrowthChildRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** DTO 校验允许稀疏测量，但拒绝负数、空指标、精度和未来日期。 */
class GrowthRequestValidationTest {
    @Test
    void sparseMetricValidationAndDatePrecisionBoundaries() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            SaveGrowthMeasurementRequest request = new SaveGrowthMeasurementRequest();
            request.setMeasureDate(LocalDate.now()); request.setHeadCm(new BigDecimal("48.50"));
            assertTrue(validator.validate(request).isEmpty());
            request.setHeadCm(null);
            assertFalse(validator.validate(request).isEmpty());
            request.setHeadCm(new BigDecimal("0"));
            assertFalse(validator.validate(request).isEmpty());
            request.setHeadCm(new BigDecimal("48.501"));
            assertFalse(validator.validate(request).isEmpty());
            request.setHeadCm(new BigDecimal("48.50")); request.setMeasureDate(LocalDate.now().plusDays(1));
            assertFalse(validator.validate(request).isEmpty());
        }
    }

    @Test
    void emptyChildRejected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(new SaveGrowthChildRequest()).isEmpty());
        }
    }
}
