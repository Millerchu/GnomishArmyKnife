package com.gak.aisubscription.vo;

import java.util.List;

/** 单个工具的原币确认成本。 */
public record AiToolCostsVO(String toolCode, List<AiCurrencySummaryVO> currencies, boolean complete) {}
