package com.gak.datamigration;

import com.gak.datamigration.dto.TravelRecordMigrationPayload;
import com.gak.datamigration.handler.MigrationResourceHandler.ImportContext;
import com.gak.datamigration.handler.TravelRecordMigrationHandler;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.travelrecord.domain.TravelGroup;
import com.gak.travelrecord.domain.TravelRecord;
import com.gak.travelrecord.mapper.TravelGroupMapper;
import com.gak.travelrecord.mapper.TravelRecordMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证迁移关系和用户隔离，而非仅验证序列化字段。 */
class TravelRecordMigrationHandlerTest {
    private final TravelRecordMapper records = mock(TravelRecordMapper.class);
    private final TravelGroupMapper groups = mock(TravelGroupMapper.class);
    private final PersonalBillMapper bills = mock(PersonalBillMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final DataMigrationArchiveService archive = mock(DataMigrationArchiveService.class);
    private final AttachmentMigrationSupport attachments = mock(AttachmentMigrationSupport.class);
    private final TravelRecordMigrationHandler handler = new TravelRecordMigrationHandler(
            records, groups, bills, users, archive, attachments);
    private final TravelRecordMigrationPayload payload = new TravelRecordMigrationPayload();
    private final ImportContext context = new ImportContext("MERGE", false, Path.of("backup"));

    @BeforeEach
    void setup() throws Exception {
        payload.setGroups(List.of());
        payload.setBills(List.of());
        payload.setRecords(List.of());
        when(archive.readJson(any(), anyString(), eq(TravelRecordMigrationPayload.class))).thenReturn(payload);
        User target = new User();
        target.setId(22L);
        when(users.selectById(22L)).thenReturn(target);
        context.mapUserId(11L, 22L);
    }

    @Test
    void restoresRelationsAndMappedOwnerWithoutGeneratingExtraBills() throws Exception {
        TravelGroup group = new TravelGroup();
        group.setId(100L);
        group.setOwnerUserId(11L);
        TravelRecord original = record(101L);
        original.setGroupId(100L);
        TravelRecord changed = record(102L);
        changed.setGroupId(100L);
        changed.setPreviousRecordId(101L);
        PersonalBill payment = new PersonalBill();
        payment.setId(103L);
        payment.setOwnerUserId(11L);
        payment.setSourceId(102L);
        payment.setSourceType("TRAVEL_RECORD");
        payment.setSourceEntry("PAYMENT");
        payload.setGroups(List.of(group));
        payload.setRecords(List.of(original, changed));
        payload.setBills(List.of(payment));

        assertEquals(4L, handler.importData(context).recordCount());
        ArgumentCaptor<TravelRecord> captured = ArgumentCaptor.forClass(TravelRecord.class);
        verify(records, times(2)).insert(captured.capture());
        TravelRecord restored = captured.getAllValues().get(1);
        assertEquals(22L, restored.getOwnerUserId());
        assertEquals(101L, restored.getPreviousRecordId());
        assertEquals(100L, restored.getGroupId());
        verify(bills).insert(argThat((PersonalBill bill) -> bill.getOwnerUserId().equals(22L)
                && bill.getSourceId().equals(102L)));
        assertEquals(11L, changed.getOwnerUserId(), "不修改导入载荷中的源用户");
    }

    @Test
    void mergeUsesStableIdWithoutDuplicateInsertion() throws Exception {
        TravelRecord existing = record(101L);
        existing.setOwnerUserId(22L);
        payload.setRecords(List.of(record(101L)));
        when(records.selectById(101L)).thenReturn(existing);
        when(records.updateById(any(TravelRecord.class))).thenReturn(1);
        handler.importData(context);
        handler.importData(context);
        verify(records, never()).insert(any(TravelRecord.class));
        verify(records, times(2)).updateById(existing);
    }

    @Test
    void rejectsCrossOwnerIdCollision() {
        TravelRecord existing = record(101L);
        existing.setOwnerUserId(99L);
        when(records.selectById(101L)).thenReturn(existing);
        payload.setRecords(List.of(record(101L)));
        assertThrows(BusinessException.class, () -> handler.importData(context));
        verify(records, never()).updateById(any(TravelRecord.class));
    }

    @Test
    void rejectsCircularAndCrossOwnerRelationsBeforeAnyWrite() {
        TravelRecord first = record(101L);
        TravelRecord second = record(102L);
        first.setPreviousRecordId(102L);
        second.setPreviousRecordId(101L);
        payload.setRecords(List.of(first, second));
        assertThrows(BusinessException.class, () -> handler.importData(context));
        second.setPreviousRecordId(null);
        second.setOwnerUserId(99L);
        assertThrows(BusinessException.class, () -> handler.importData(context));
        verifyNoInteractions(records, groups, bills);
    }

    @Test
    void restoresDetachedBillAlongWithItsReference() throws Exception {
        TravelRecord record = record(101L);
        record.setBillingDetached(true);
        record.setDetachedPaymentBillId(103L);
        PersonalBill detached = new PersonalBill();
        detached.setId(103L);
        detached.setOwnerUserId(11L);
        payload.setRecords(List.of(record));
        payload.setBills(List.of(detached));
        handler.importData(context);
        verify(bills).insert(argThat((PersonalBill bill) -> bill.getId().equals(103L)
                && bill.getOwnerUserId().equals(22L) && bill.getSourceType() == null));
    }

    private TravelRecord record(Long id) {
        TravelRecord record = new TravelRecord();
        record.setId(id);
        record.setOwnerUserId(11L);
        record.setVersion(0);
        return record;
    }
}
