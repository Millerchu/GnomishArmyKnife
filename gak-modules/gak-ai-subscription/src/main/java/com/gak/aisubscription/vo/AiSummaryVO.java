package com.gak.aisubscription.vo;
import java.time.LocalDateTime;
import java.util.List;
/** 确认金额与缺失原因一并返回。 */
public record AiSummaryVO(String month, boolean hasRecords, List<AiCurrencySummaryVO> currencies, boolean complete, List<String> missingReasons, LocalDateTime updatedAt) {}
