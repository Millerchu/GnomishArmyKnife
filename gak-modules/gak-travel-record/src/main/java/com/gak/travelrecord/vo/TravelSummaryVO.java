package com.gak.travelrecord.vo;

import java.math.BigDecimal;
import java.util.Map;

/** 次数只统计实际完成的交通段，费用按币种独立统计。 */
public record TravelSummaryVO(long completedCount, long unknownAmountCount,
                              Map<String, Long> typeCounts, Map<String, Long> routeCounts,
                              Map<String, BigDecimal> netAmounts) { }
