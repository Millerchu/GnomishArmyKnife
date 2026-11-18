package com.gak.aisubscription.vo;
import java.time.*;
import java.math.BigDecimal;
/** 智订查询结果，不含凭据。 */
public record AiPaymentVO(Long id, Long accountId, Long subscriptionId, String type, BigDecimal amount, String currency, LocalDate paymentDate, LocalDate coverageStart, LocalDate coverageEnd, Long originalPaymentId, String requestKey, String note, LocalDateTime recordedAt) {}
