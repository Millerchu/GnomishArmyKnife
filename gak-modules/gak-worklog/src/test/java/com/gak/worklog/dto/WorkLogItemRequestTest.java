package com.gak.worklog.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkLogItemRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesContentAndPersonDayIndependently() {
        WorkLogItemRequest item = new WorkLogItemRequest();
        item.setContent("出差返程");
        item.setPersonDay(new BigDecimal("0.5"));
        assertTrue(validator.validate(item).isEmpty());

        item.setContent(" ");
        assertFalse(validator.validate(item).isEmpty());
        item.setContent("工作内容");
        item.setPersonDay(new BigDecimal("1.1"));
        assertFalse(validator.validate(item).isEmpty());
        item.setPersonDay(new BigDecimal("0.55"));
        assertFalse(validator.validate(item).isEmpty());
        item.setPersonDay(null);
        assertTrue(validator.validate(item).isEmpty());
    }
}
