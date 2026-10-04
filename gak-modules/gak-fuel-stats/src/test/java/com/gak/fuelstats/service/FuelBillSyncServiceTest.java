package com.gak.fuelstats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.fuelstats.domain.FuelRecord;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 来源同步应保持幂等、实付口径及历史兼容。 */
class FuelBillSyncServiceTest {
    private FuelRecord record() {
        FuelRecord record = new FuelRecord();
        record.setId(20L);
        record.setOwnerUserId(1L);
        record.setVehicleName("测试车辆");
        record.setFuelType("95");
        record.setDiscountedAmount(new BigDecimal("250.25"));
        record.setTotalAmount(new BigDecimal("300.00"));
        record.setFuelDate(LocalDate.of(2026, 10, 4));
        record.setStationName("测试油站");
        return record;
    }

    @Test
    void shouldCreateExpenseUsingPaidAmountAndUniqueSource() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        new FuelBillSyncService(mapper).sync(record(), true);
        ArgumentCaptor<PersonalBill> saved = ArgumentCaptor.forClass(PersonalBill.class);
        verify(mapper).insert(saved.capture());
        PersonalBill bill = saved.getValue();
        assertEquals(new BigDecimal("250.25"), bill.getAmount());
        assertEquals("EXPENSE", bill.getBillType());
        assertEquals("CNY", bill.getCurrency());
        assertEquals("交通", bill.getCategoryName());
        assertEquals("FUEL_RECORD", bill.getSourceType());
        assertEquals(20L, bill.getSourceId());
        assertEquals(1L, bill.getOwnerUserId());
        assertEquals("PAYMENT", bill.getSourceEntry());
        assertEquals(record().getFuelDate(), bill.getBillDate());
        assertEquals("测试油站", bill.getMerchantName());
    }

    @Test
    void shouldUpdateSameBillOnRepeatedSyncAndPreserveCreatedTime() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        PersonalBill existing = new PersonalBill();
        existing.setId(30L);
        existing.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(mapper.selectOne(any())).thenReturn(existing);
        FuelRecord record = record();
        record.setDiscountedAmount(new BigDecimal("80.00"));
        FuelBillSyncService sync = new FuelBillSyncService(mapper);
        sync.sync(record, true);
        sync.sync(record, false);
        verify(mapper, never()).insert(any(PersonalBill.class));
        verify(mapper, times(2)).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
        assertEquals(30L, existing.getId());
        assertEquals(new BigDecimal("80.00"), existing.getAmount());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), existing.getCreatedAt());
    }

    @Test
    void shouldNotCreateBillWhenEditingUnlinkedHistoricalRecord() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        new FuelBillSyncService(mapper).sync(record(), false);
        verify(mapper, never()).insert(any(PersonalBill.class));
        verify(mapper, never()).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
    }

    @Test
    void shouldExplicitlyClearMerchantWhenStationRemoved() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        PersonalBill existing = new PersonalBill();
        existing.setId(30L);
        existing.setMerchantName("旧油站");
        when(mapper.selectOne(any())).thenReturn(existing);
        FuelRecord record = record();
        record.setStationName(null);
        new FuelBillSyncService(mapper).sync(record, false);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PersonalBill>> update =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        org.junit.jupiter.api.Assertions.assertTrue(update.getValue().getSqlSet().contains("merchant_name="));
        org.junit.jupiter.api.Assertions.assertTrue(update.getValue().getParamNameValuePairs().containsValue(null));
    }

    @Test
    void shouldDeleteOnlyBillWithMatchingOwnerRecordAndEntry() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        new FuelBillSyncService(mapper).delete(1L, 20L);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<PersonalBill>> query =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).delete(query.capture());
        String sql = query.getValue().getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("owner_user_id"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("source_id"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("source_type"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("source_entry"));
    }
}
