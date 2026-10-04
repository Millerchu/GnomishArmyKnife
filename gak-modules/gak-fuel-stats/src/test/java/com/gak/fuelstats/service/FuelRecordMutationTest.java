package com.gak.fuelstats.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.dto.SaveFuelRecordRequest;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

/** 覆盖源服务编排、用户隔离以及同步失败的事务回滚边界。 */
class FuelRecordMutationTest {
    private FuelRecordMapper records;
    private FuelBillSyncService bills;
    private AttachmentService attachments;
    private PlatformTransactionManager transactions;
    private SimpleTransactionStatus transaction;
    private FuelRecordService service;
    private FuelRecord record;

    @BeforeEach
    void setUp() {
        records = mock(FuelRecordMapper.class);
        bills = mock(FuelBillSyncService.class);
        attachments = mock(AttachmentService.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectById(1L)).thenReturn(new User());
        transactions = mock(PlatformTransactionManager.class);
        transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        FuelRecordService target = new FuelRecordService(records, users, attachments, bills);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (FuelRecordService) factory.getProxy();
        record = new FuelRecord();
        record.setId(20L);
        record.setOwnerUserId(1L);
        record.setVehicleName("测试车辆");
        record.setFuelDate(LocalDate.of(2026, 10, 4));
        record.setOdometerKm(new BigDecimal("1000"));
        record.setFuelVolume(new BigDecimal("40"));
        record.setDiscountedAmount(new BigDecimal("250"));
        record.setTotalAmount(new BigDecimal("300"));
        record.setFillType("FULL");
        when(records.selectOwnedForUpdate(20L, 1L)).thenReturn(record);
        when(records.selectList(any())).thenReturn(List.of(record));
    }

    private SaveFuelRecordRequest request() {
        SaveFuelRecordRequest request = new SaveFuelRecordRequest();
        request.setVehicleName("测试车辆");
        request.setFuelDate(record.getFuelDate());
        request.setOdometerKm(new BigDecimal("1000"));
        request.setFuelVolume(new BigDecimal("40"));
        request.setDiscountedAmount(new BigDecimal("250"));
        request.setTotalAmount(new BigDecimal("300"));
        request.setFuelType("95");
        request.setFillType("FULL");
        return request;
    }

    @Test
    void shouldCreateAndSyncWithinOneCommittedTransaction() {
        doAnswer(invocation -> {
            FuelRecord inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        }).when(records).insert(any(FuelRecord.class));
        service.create(1L, request());
        verify(bills).sync(argThat(item -> item.getId().equals(20L)
                && item.getOwnerUserId().equals(1L)), eq(true));
        verify(transactions).commit(transaction);
    }

    @Test
    void shouldLockSourceBeforeUpdatingLinkedBill() {
        service.update(1L, 20L, request());
        var order = inOrder(records, bills);
        order.verify(records).selectOwnedForUpdate(20L, 1L);
        order.verify(records).updateById(record);
        order.verify(bills).sync(record, false);
    }

    @Test
    void shouldRemoveLinkedBillBeforeDeletingSource() {
        service.delete(1L, 20L);
        var order = inOrder(records, bills);
        order.verify(records).selectOwnedForUpdate(20L, 1L);
        order.verify(bills).delete(1L, 20L);
        order.verify(records).deleteById(20L);
    }

    @Test
    void shouldRejectOtherOwnersWithoutTouchingBills() {
        record.setOwnerUserId(2L);
        assertThrows(ResponseStatusException.class, () -> service.delete(1L, 20L));
        verifyNoInteractions(bills, attachments);
        verify(records, never()).deleteById(any(Long.class));
    }

    @Test
    void shouldRollbackWhenBillSyncFails() {
        doThrow(new IllegalStateException("账单写入失败")).when(bills).sync(any(), eq(false));
        assertThrows(IllegalStateException.class, () -> service.update(1L, 20L, request()));
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }
}
