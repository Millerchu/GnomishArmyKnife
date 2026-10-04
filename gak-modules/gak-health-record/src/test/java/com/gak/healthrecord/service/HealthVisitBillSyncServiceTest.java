package com.gak.healthrecord.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 就诊来源支出金额、身份、删除范围及隐私约束。 */
class HealthVisitBillSyncServiceTest {
    private HealthVisit visit() {
        HealthVisit visit = new HealthVisit();
        visit.setId(20L);
        visit.setOwnerUserId(1L);
        visit.setAmount(new BigDecimal("128.50"));
        visit.setVisitDate(LocalDate.of(2026, 10, 4));
        visit.setHospitalName("测试医院");
        visit.setDiagnosisSummary("不应出现在账单的诊断");
        return visit;
    }

    @Test
    void shouldCreateMedicalExpenseWithoutDiagnosis() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        new HealthVisitBillSyncService(mapper).sync(visit());
        ArgumentCaptor<PersonalBill> saved = ArgumentCaptor.forClass(PersonalBill.class);
        verify(mapper).insert(saved.capture());
        PersonalBill bill = saved.getValue();
        assertEquals(new BigDecimal("128.50"), bill.getAmount());
        assertEquals("医疗", bill.getCategoryName());
        assertEquals("EXPENSE", bill.getBillType());
        assertEquals("CNY", bill.getCurrency());
        assertEquals("HEALTH_VISIT", bill.getSourceType());
        assertEquals("PAYMENT", bill.getSourceEntry());
        assertEquals(20L, bill.getSourceId());
        assertEquals(1L, bill.getOwnerUserId());
        assertEquals("测试医院", bill.getMerchantName());
        assertEquals(visit().getVisitDate(), bill.getBillDate());
        assertEquals("医院就诊", bill.getNote());
    }

    @Test
    void shouldUpdateSameSourceInsteadOfInsertingDuplicate() {
        PersonalBillMapper mapper = mock(PersonalBillMapper.class);
        PersonalBill existing = new PersonalBill();
        existing.setId(30L);
        when(mapper.selectOne(any())).thenReturn(existing);
        HealthVisit visit = visit();
        visit.setAmount(new BigDecimal("100.00"));
        visit.setHospitalName("复诊医院");
        HealthVisitBillSyncService sync = new HealthVisitBillSyncService(mapper);
        sync.sync(visit);
        sync.sync(visit);
        verify(mapper, never()).insert(any(PersonalBill.class));
        ArgumentCaptor<UpdateWrapper<PersonalBill>> updates = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper, times(2)).update(isNull(), updates.capture());
        UpdateWrapper<PersonalBill> update = updates.getValue();
        assertTrue(update.getSqlSet().contains("amount="));
        update.getSqlSegment();
        assertTrue(update.getParamNameValuePairs().containsValue(new BigDecimal("100.00")));
        assertTrue(update.getParamNameValuePairs().containsValue("复诊医院"));
        assertTrue(update.getParamNameValuePairs().containsValue(30L));
    }

    @Test
    void shouldRemoveOnlyMatchingSourceForEmptyAndZeroAmount() {
        for (BigDecimal amount : new BigDecimal[]{null, BigDecimal.ZERO}) {
            PersonalBillMapper mapper = mock(PersonalBillMapper.class);
            HealthVisit visit = visit();
            visit.setAmount(amount);
            new HealthVisitBillSyncService(mapper).sync(visit);
            ArgumentCaptor<QueryWrapper<PersonalBill>> query = ArgumentCaptor.forClass(QueryWrapper.class);
            verify(mapper).delete(query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("owner_user_id"));
            assertTrue(query.getValue().getSqlSegment().contains("source_entry"));
            assertTrue(query.getValue().getParamNameValuePairs().containsValue(1L));
            assertTrue(query.getValue().getParamNameValuePairs().containsValue(20L));
            assertTrue(query.getValue().getParamNameValuePairs().containsValue("HEALTH_VISIT"));
            verify(mapper, never()).insert(any(PersonalBill.class));
        }
    }
}
