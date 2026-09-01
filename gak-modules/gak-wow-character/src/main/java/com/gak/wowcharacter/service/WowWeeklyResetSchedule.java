package com.gak.wowcharacter.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * 国服 WoW 每周内容重置周期。
 */
public final class WowWeeklyResetSchedule {

    public static final ZoneId CHINA_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DayOfWeek RESET_DAY = DayOfWeek.THURSDAY;
    private static final LocalTime RESET_TIME = LocalTime.of(7, 0);

    private WowWeeklyResetSchedule() {
    }

    /**
     * 计算指定国服时间所属周的重置日期；周四 07:00 前仍归属上一周期。
     */
    public static LocalDate resolveWeekStartDate(LocalDateTime chinaNow) {
        LocalDate latestThursday = chinaNow.toLocalDate().with(TemporalAdjusters.previousOrSame(RESET_DAY));
        if (chinaNow.getDayOfWeek() == RESET_DAY && chinaNow.toLocalTime().isBefore(RESET_TIME)) {
            return latestThursday.minusWeeks(1);
        }
        return latestThursday;
    }

    /**
     * 获取当前国服周期起始日，供低保保存、展示和数据迁移统一使用。
     */
    public static LocalDate currentWeekStartDate() {
        return resolveWeekStartDate(LocalDateTime.now(CHINA_ZONE_ID));
    }
}
