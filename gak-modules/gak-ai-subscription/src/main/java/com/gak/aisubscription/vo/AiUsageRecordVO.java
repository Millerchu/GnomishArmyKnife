package com.gak.aisubscription.vo;
import java.time.*;
import java.math.BigDecimal;
/** 智订查询结果，不含凭据。 */
public record AiUsageRecordVO(Long id, Long accountId, String kind, String mode, String periodKey, String zoneId, LocalDate startDate, LocalDate endDate, String metric, String unit, String dimension, BigDecimal quantity, BigDecimal cost, String currency, String resolution, String source, String status, String reason, LocalDateTime recordedAt) {}
