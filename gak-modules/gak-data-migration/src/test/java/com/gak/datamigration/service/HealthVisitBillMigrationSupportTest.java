package com.gak.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthVisitBillSyncService;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 健康备份恢复时的来源映射和用户隔离。 */
class HealthVisitBillMigrationSupportTest {
    @Test
    void shouldRestoreUsingMappedVisitAndCurrentMergedAmount() {
        PersonalBillMapper bills = mock(PersonalBillMapper.class);
        HealthVisitMapper visits = mock(HealthVisitMapper.class);
        HealthVisitBillSyncService sync = mock(HealthVisitBillSyncService.class);
        HealthVisit target = target();
        when(visits.selectById(200L)).thenReturn(target);
        when(bills.selectCount(any())).thenReturn(0L);
        new HealthVisitBillMigrationSupport(bills, visits, sync)
                .restore(List.of(source()), Map.of(20L, 200L), owner -> 100L);
        verify(sync).sync(target);
    }

    @Test
    void shouldKeepUnlinkedOldBackupUnbilledButMaintainExistingSource() {
        PersonalBillMapper bills = mock(PersonalBillMapper.class);
        HealthVisitMapper visits = mock(HealthVisitMapper.class);
        HealthVisitBillSyncService sync = mock(HealthVisitBillSyncService.class);
        HealthVisit target = target();
        when(visits.selectById(200L)).thenReturn(target);
        when(bills.selectCount(any())).thenReturn(0L, 1L);
        HealthVisitBillMigrationSupport support = new HealthVisitBillMigrationSupport(bills, visits, sync);
        support.restore(List.of(), Map.of(20L, 200L), owner -> 100L);
        verifyNoInteractions(sync);
        target.setAmount(null);
        support.restore(List.of(), Map.of(20L, 200L), owner -> 100L);
        verify(sync).sync(target);
    }

    @Test
    void shouldRejectMissingSourceAndCrossUserAssociation() {
        PersonalBillMapper bills = mock(PersonalBillMapper.class);
        HealthVisitMapper visits = mock(HealthVisitMapper.class);
        HealthVisitBillSyncService sync = mock(HealthVisitBillSyncService.class);
        HealthVisitBillMigrationSupport support = new HealthVisitBillMigrationSupport(bills, visits, sync);
        assertThrows(BusinessException.class, () -> support.restore(List.of(source()), Map.of(), owner -> 100L));
        when(visits.selectById(200L)).thenReturn(target());
        assertThrows(BusinessException.class,
                () -> support.restore(List.of(source()), Map.of(20L, 200L), owner -> 101L));
        verifyNoInteractions(sync);
    }

    private HealthVisit target() {
        HealthVisit target = new HealthVisit();
        target.setId(200L);
        target.setOwnerUserId(100L);
        target.setAmount(new BigDecimal("100.00"));
        return target;
    }

    private PersonalBill source() {
        PersonalBill bill = new PersonalBill();
        bill.setSourceType("HEALTH_VISIT");
        bill.setSourceEntry("PAYMENT");
        bill.setSourceId(20L);
        bill.setOwnerUserId(1L);
        bill.setAmount(new BigDecimal("128.50"));
        return bill;
    }
}
