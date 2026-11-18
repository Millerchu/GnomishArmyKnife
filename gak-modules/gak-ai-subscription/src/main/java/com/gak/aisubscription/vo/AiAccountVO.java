package com.gak.aisubscription.vo;
import java.time.*;
import java.math.BigDecimal;
/** 智订查询结果，不含凭据。 */
public record AiAccountVO(Long id, String toolCode, String alias, String note, Long passwordMemoId, Boolean archived, LocalDateTime recordedAt, boolean passwordMemoAvailable, String passwordMemoSummary) {}
