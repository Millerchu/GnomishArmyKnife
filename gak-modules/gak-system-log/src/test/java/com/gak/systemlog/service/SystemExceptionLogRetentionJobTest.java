package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gak.systemlog.config.SystemLogRetentionProperties;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.UncategorizedSQLException;

/** 固定上海时间、批次停止条件、失败封闭与抢锁失败。 */
class SystemExceptionLogRetentionJobTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T23:30:00Z"), ZoneId.of("Asia/Shanghai"));
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 10, 12, 7, 30);
    private final SystemLogRetentionProperties properties = new SystemLogRetentionProperties();
    private final SystemExceptionLogPurger purger = mock(SystemExceptionLogPurger.class);

    @Test
    void usesShanghaiClockAndStopsOnPartialBatch() {
        properties.setBatchIntervalMs(0);
        when(purger.purgeOneBatch(cutoff, 500)).thenReturn(OptionalInt.of(500), OptionalInt.of(17));
        job().cleanup();
        verify(purger, times(2)).purgeOneBatch(cutoff, 500);
    }

    @Test
    void stopsAtBatchLimit() {
        properties.setBatchIntervalMs(0);
        when(purger.purgeOneBatch(cutoff, 500)).thenReturn(OptionalInt.of(500));
        job().cleanup();
        verify(purger, times(40)).purgeOneBatch(cutoff, 500);
    }

    @Test
    void emptyTableAndMissingLockStopImmediately() {
        for (OptionalInt result : new OptionalInt[] {OptionalInt.of(0), OptionalInt.empty()}) {
            reset(purger);
            when(purger.purgeOneBatch(cutoff, 500)).thenReturn(result);
            assertDoesNotThrow(() -> job().cleanup());
            verify(purger).purgeOneBatch(cutoff, 500);
        }
    }

    @Test
    void purgerSetsLocalStatementTimeoutBeforeLockAndNeverDeletesWithoutLock() {
        SystemExceptionLogMapper mapper = mock(SystemExceptionLogMapper.class);
        var batch = new SystemExceptionLogPurger(mapper, properties);
        assertTrue(batch.purgeOneBatch(cutoff, 500).isEmpty());
        var order = inOrder(mapper);
        order.verify(mapper).setCleanupStatementTimeout("10000ms");
        order.verify(mapper).tryCleanupLock(anyInt(), anyInt());
        verify(mapper, never()).purgeExpired(any(), anyInt());

        when(mapper.tryCleanupLock(anyInt(), anyInt())).thenReturn(true);
        when(mapper.purgeExpired(cutoff, 500)).thenReturn(8);
        assertEquals(OptionalInt.of(8), batch.purgeOneBatch(cutoff, 500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"42501", "42883", "55P03", "57014"})
    void sqlFailuresNeverEscapeOrCallCapture(String sqlState) {
        SystemExceptionLogMapper mapper = mock(SystemExceptionLogMapper.class);
        when(mapper.tryCleanupLock(anyInt(), anyInt())).thenReturn(true);
        when(mapper.purgeExpired(any(), anyInt())).thenThrow(new UncategorizedSQLException(
                "synthetic", "synthetic", new SQLException("synthetic", sqlState)));
        var batch = new SystemExceptionLogPurger(mapper, properties);
        assertDoesNotThrow(() -> new SystemExceptionLogRetentionJob(batch, properties, clock, 30).cleanup());
        verify(mapper).purgeExpired(cutoff, 500);
    }

    @Test
    void restoresInterruptAndDoesNotPassFailureToScheduler() {
        when(purger.purgeOneBatch(cutoff, 500)).thenReturn(OptionalInt.of(500));
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(() -> job().cleanup());
            assertTrue(Thread.currentThread().isInterrupted());
            verify(purger).purgeOneBatch(cutoff, 500);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unexpectedRuntimeAndErrorDoNotEscape() {
        for (Throwable failure : new Throwable[] {new IllegalStateException(), new AssertionError()}) {
            reset(purger);
            when(purger.purgeOneBatch(any(), anyInt())).thenThrow(failure);
            assertDoesNotThrow(() -> job().cleanup());
        }
    }

    private SystemExceptionLogRetentionJob job() {
        return new SystemExceptionLogRetentionJob(purger, properties, clock, 30);
    }
}
