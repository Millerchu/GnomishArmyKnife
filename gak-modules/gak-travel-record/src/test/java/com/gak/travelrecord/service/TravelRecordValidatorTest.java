package com.gak.travelrecord.service;

import com.gak.framework.exception.BusinessException;
import com.gak.travelrecord.dto.SaveTravelRecordRequest;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelRecordValidatorTest {
    private final TravelRecordValidator validator = new TravelRecordValidator();
    @Test void acceptsDateOnlyAndUnknownAmount() { assertDoesNotThrow(() -> validator.validate(request())); }
    @Test void comparesInternationalTimesAsInstants() {
        SaveTravelRecordRequest request = request();
        request.setDepartureZone("Asia/Shanghai"); request.setDepartureTime(LocalTime.of(13, 0));
        request.setArrivalDate(request.getDepartureDate()); request.setArrivalZone("America/Los_Angeles");
        request.setArrivalTime(LocalTime.of(9, 0));
        assertDoesNotThrow(() -> validator.validate(request));
        request.setArrivalTime(LocalTime.of(0, 0)); request.setArrivalZone("Asia/Shanghai");
        assertThrows(BusinessException.class, () -> validator.validate(request));
    }
    @Test void requiresDateForRefundAndExplicitPaymentFallback() {
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("100"));
        assertThrows(BusinessException.class, () -> validator.validate(request));
        request.setUseDepartureDate(true);
        assertDoesNotThrow(() -> validator.validate(request));
        request.setRefundAmount(new BigDecimal("20"));
        assertThrows(BusinessException.class, () -> validator.validate(request));
        request.setRefundDate(request.getDepartureDate().minusDays(1));
        assertDoesNotThrow(() -> validator.validate(request));
        request.setRefundAmount(new BigDecimal("101"));
        assertThrows(BusinessException.class, () -> validator.validate(request));
    }
    @Test void validatesCurrencyZoneAndArrivalDate() {
        SaveTravelRecordRequest request = request(); request.setCurrency("ZZZ");
        assertThrows(BusinessException.class, () -> validator.validate(request));
        request.setCurrency("CNY"); request.setDepartureZone("not-a-zone");
        assertThrows(BusinessException.class, () -> validator.validate(request));
        request.setDepartureZone("Asia/Shanghai"); request.setArrivalTime(LocalTime.NOON);
        assertThrows(BusinessException.class, () -> validator.validate(request));
    }
    private SaveTravelRecordRequest request() {
        SaveTravelRecordRequest value = new SaveTravelRecordRequest(); value.setTransportType("TRAIN");
        value.setStatus("PLANNED"); value.setCurrency("CNY"); value.setDepartureDate(LocalDate.of(2010, 1, 1));
        value.setDepartureZone("Asia/Shanghai"); value.setArrivalZone("Asia/Shanghai");
        value.setSyncBill(true); value.setUseDepartureDate(false); return value;
    }
}
