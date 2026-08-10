package com.gak.wowcharacter.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WowWeeklyResetScheduleTest {

    @Test
    void shouldUsePreviousThursdayBeforeThursdaySevenAm() {
        LocalDate weekStartDate = WowWeeklyResetSchedule.resolveWeekStartDate(
                LocalDateTime.of(2026, 8, 13, 6, 59)
        );

        assertEquals(LocalDate.of(2026, 8, 6), weekStartDate);
    }

    @Test
    void shouldUseCurrentThursdayFromThursdaySevenAm() {
        LocalDate weekStartDate = WowWeeklyResetSchedule.resolveWeekStartDate(
                LocalDateTime.of(2026, 8, 13, 7, 0)
        );

        assertEquals(LocalDate.of(2026, 8, 13), weekStartDate);
    }

    @Test
    void shouldKeepCurrentThursdayForOtherDays() {
        LocalDate weekStartDate = WowWeeklyResetSchedule.resolveWeekStartDate(
                LocalDateTime.of(2026, 8, 16, 21, 30)
        );

        assertEquals(LocalDate.of(2026, 8, 13), weekStartDate);
    }
}
