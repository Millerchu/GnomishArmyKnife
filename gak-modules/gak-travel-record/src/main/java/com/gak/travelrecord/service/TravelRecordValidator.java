package com.gak.travelrecord.service;

import com.gak.framework.exception.BusinessException;
import com.gak.travelrecord.dto.SaveTravelRecordRequest;
import com.gak.travelrecord.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Currency;
import org.springframework.stereotype.Component;

/** 日期不完整时不推测时间；国际行程使用起终点各自的时区比较。 */
@Component
public class TravelRecordValidator {
    public void validate(SaveTravelRecordRequest request) {
        try {
            TravelTransportType.valueOf(request.getTransportType());
            TravelStatus.valueOf(request.getStatus());
            if (request.getPurpose() != null && !request.getPurpose().isBlank()) {
                TravelPurpose.valueOf(request.getPurpose());
            }
            Currency.getInstance(request.getCurrency());
            ZoneId.of(request.getDepartureZone());
            ZoneId.of(request.getArrivalZone());
        } catch (IllegalArgumentException | DateTimeException | NullPointerException exception) {
            throw new BusinessException("TRAVEL_VALUE_INVALID", "请选择有效的交通类型、状态、用途、币种和时区");
        }
        validateDates(request);
        validateMoney(request);
    }

    private void validateDates(SaveTravelRecordRequest request) {
        if (request.getArrivalTime() != null && request.getArrivalDate() == null) {
            throw new BusinessException("TRAVEL_ARRIVAL_DATE_REQUIRED", "填写到达时间时需要到达日期");
        }
        if (request.getArrivalDate() != null && request.getDepartureTime() != null && request.getArrivalTime() != null) {
            Instant departure = request.getDepartureDate().atTime(request.getDepartureTime())
                    .atZone(ZoneId.of(request.getDepartureZone())).toInstant();
            Instant arrival = request.getArrivalDate().atTime(request.getArrivalTime())
                    .atZone(ZoneId.of(request.getArrivalZone())).toInstant();
            if (arrival.isBefore(departure)) {
                throw new BusinessException("TRAVEL_TIME_INVALID", "按起终点时区计算，到达时间不能早于出发时间");
            }
        } else if (request.getArrivalDate() != null
                && request.getDepartureZone().equals(request.getArrivalZone())
                && request.getArrivalDate().isBefore(request.getDepartureDate())) {
            throw new BusinessException("TRAVEL_TIME_INVALID", "相同时区的到达日期不能早于出发日期");
        }
    }

    private void validateMoney(SaveTravelRecordRequest request) {
        BigDecimal paid = request.getPaidAmount();
        BigDecimal refund = request.getRefundAmount();
        if ((paid != null && paid.signum() < 0) || (refund != null && refund.signum() < 0)) {
            throw new BusinessException("TRAVEL_MONEY_INVALID", "支付与退款金额不能为负数");
        }
        if (refund != null && refund.signum() > 0 && (paid == null || refund.compareTo(paid) > 0)) {
            throw new BusinessException("TRAVEL_REFUND_INVALID", "退款不能超过已支付金额");
        }
        if (refund != null && refund.signum() > 0 && request.getRefundDate() == null) {
            throw new BusinessException("TRAVEL_REFUND_DATE_REQUIRED", "请填写实际退款日期");
        }
        if (Boolean.TRUE.equals(request.getSyncBill()) && paid != null && paid.signum() > 0
                && request.getPaymentDate() == null && !Boolean.TRUE.equals(request.getUseDepartureDate())) {
            throw new BusinessException("TRAVEL_PAYMENT_DATE_REQUIRED", "请填写支付日期，或明确选择以出发日期代记");
        }
        LocalDate paidDate = request.getPaymentDate() == null && Boolean.TRUE.equals(request.getUseDepartureDate())
                ? request.getDepartureDate() : request.getPaymentDate();
        if (request.getRefundDate() != null && paidDate != null && request.getRefundDate().isBefore(paidDate)
                && !Boolean.TRUE.equals(request.getUseDepartureDate())) {
            throw new BusinessException("TRAVEL_REFUND_DATE_INVALID", "退款日期不能早于实际支付日期");
        }
        if (request.getExistingBillId() != null && (!Boolean.TRUE.equals(request.getSyncBill()) || paid == null || paid.signum() <= 0)) {
            throw new BusinessException("TRAVEL_LINK_INVALID", "关联已有账单需要开启同步并填写支付金额");
        }
    }
}
