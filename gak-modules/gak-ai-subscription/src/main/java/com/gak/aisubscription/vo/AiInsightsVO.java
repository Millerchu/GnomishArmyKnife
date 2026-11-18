package com.gak.aisubscription.vo;

import java.util.List;

/** 复用月度汇总规则生成趋势与工具明细。 */
public record AiInsightsVO(List<AiSummaryVO> trend, List<AiToolCostsVO> tools) {}
