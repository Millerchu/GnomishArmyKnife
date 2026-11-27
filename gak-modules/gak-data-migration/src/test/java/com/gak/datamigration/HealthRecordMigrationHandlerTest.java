package com.gak.datamigration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.datamigration.dto.HealthRecordMigrationPayload;
import com.gak.datamigration.dto.HealthReportDetailMigrationItem;
import com.gak.datamigration.handler.HealthRecordMigrationHandler;
import com.gak.datamigration.handler.MigrationResourceHandler.ExportContext;
import com.gak.datamigration.handler.MigrationResourceHandler.ImportContext;
import com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData;
import com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceImportResult;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.HealthVisitBillMigrationSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.domain.HealthReportDetailItem;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthReportDetailService;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 健康记录迁移处理器测试，覆盖旧包兼容、新包明细重映射、同日多笔和冲突规则。
 */
class HealthRecordMigrationHandlerTest {

    private final HealthRecordMapper recordMapper = mock(HealthRecordMapper.class);
    private final HealthVisitMapper visitMapper = mock(HealthVisitMapper.class);
    private final HealthReportMapper reportMapper = mock(HealthReportMapper.class);
    private final HealthReportDetailMapper detailMapper = mock(HealthReportDetailMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final DataMigrationArchiveService archiveService = mock(DataMigrationArchiveService.class);
    private final AttachmentMigrationSupport attachmentSupport = mock(AttachmentMigrationSupport.class);
    private final HealthVisitBillMigrationSupport billSupport = mock(HealthVisitBillMigrationSupport.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final HealthReportDetailService detailService = new HealthReportDetailService(
            reportMapper, recordMapper, detailMapper, userMapper, attachmentService);

    private HealthRecordMigrationHandler handler;
    private ImportContext mergeContext;

    @BeforeEach
    void setUp() {
        handler = new HealthRecordMigrationHandler(
                recordMapper,
                visitMapper,
                reportMapper,
                detailMapper,
                detailService,
                userMapper,
                archiveService,
                attachmentSupport,
                billSupport,
                "./target/health-migration-test",
                "/api/health-records/report-files/"
        );
        mergeContext = new ImportContext("MERGE", false, Path.of("backup"));
        mergeContext.mapUserId(11L, 22L);
        User targetUser = new User();
        targetUser.setId(22L);
        when(userMapper.selectById(22L)).thenReturn(targetUser);
        when(billSupport.exportBills()).thenReturn(List.of());
    }

    @Test
    void shouldExportReportDetailsAndIncludeThemInRecordCountWithoutAttachments() {
        HealthRecord rec = buildRecord(101L, 11L, LocalDate.of(2026, 5, 10));
        HealthReport rep = buildReport(201L, 11L, LocalDate.of(2026, 5, 10), "年度体检");
        HealthReportDetail detail = new HealthReportDetail();
        detail.setReportId(201L);
        detail.setRecordId(101L);
        detail.setRevision(2);
        detail.setItems(List.of(buildItem("URINALYSIS", "urineSpecificGravity", "尿比重", "NUMBER", "1.012")));
        detail.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 10, 0));

        when(recordMapper.selectList(any())).thenReturn(List.of(rec));
        when(visitMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of(rep));
        when(detailMapper.selectAllOrdered()).thenReturn(List.of(detail));

        MigrationResourceExportData exported = handler.exportData(new ExportContext(false));
        assertThat(exported.recordCount()).isEqualTo(3L);
        HealthRecordMigrationPayload payload = (HealthRecordMigrationPayload) exported.payload();
        assertThat(payload.getReportDetails()).hasSize(1);
        assertThat(payload.getReportDetails().get(0).getReportId()).isEqualTo(201L);
        assertThat(payload.getReportDetails().get(0).getRecordId()).isEqualTo(101L);
    }

    @Test
    void shouldPreserveExistingDetailsWhenLegacyPackageOmitsReportDetails() throws Exception {
        HealthRecordMigrationPayload legacyPayload = new HealthRecordMigrationPayload();
        legacyPayload.setRecords(List.of(buildRecord(101L, 11L, LocalDate.of(2026, 5, 10))));
        legacyPayload.setReports(List.of(buildReport(201L, 11L, LocalDate.of(2026, 5, 10), "年度体检")));
        legacyPayload.setReportDetails(null);

        when(archiveService.readJson(any(), anyString(), eq(HealthRecordMigrationPayload.class))).thenReturn(legacyPayload);
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of());

        MigrationResourceImportResult result = handler.importData(mergeContext);
        assertThat(result.recordCount()).isEqualTo(2L);
        verify(detailMapper, never()).insertDetail(any());
        verify(detailMapper, never()).updateDetailWithRevision(any(), anyInt());
    }

    @Test
    void shouldRemapIdsAndImportSameDayMultipleReportsWithDetails() throws Exception {
        LocalDate sameDay = LocalDate.of(2026, 5, 10);
        HealthRecord r1 = buildRecord(101L, 11L, sameDay);
        HealthRecord r2 = buildRecord(102L, 11L, sameDay);
        HealthReport p1 = buildReport(201L, 11L, sameDay, "体检A");
        HealthReport p2 = buildReport(202L, 11L, sameDay, "体检B");

        HealthReportDetailMigrationItem d1 = new HealthReportDetailMigrationItem(
                201L, 101L, List.of(buildItem("URINALYSIS", "urineSpecificGravity", "尿比重", "NUMBER", "1.012")),
                LocalDateTime.of(2026, 5, 10, 12, 0));
        HealthReportDetailMigrationItem d2 = new HealthReportDetailMigrationItem(
                202L, 102L, List.of(), LocalDateTime.of(2026, 5, 10, 12, 5));

        HealthRecordMigrationPayload payload = new HealthRecordMigrationPayload();
        payload.setRecords(List.of(r1, r2));
        payload.setReports(List.of(p1, p2));
        payload.setReportDetails(List.of(d1, d2));

        when(archiveService.readJson(any(), anyString(), eq(HealthRecordMigrationPayload.class))).thenReturn(payload);
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectById(201L)).thenReturn(buildReport(201L, 22L, sameDay, "体检A"));
        when(reportMapper.selectById(202L)).thenReturn(buildReport(202L, 22L, sameDay, "体检B"));
        when(recordMapper.selectById(101L)).thenReturn(null, buildRecord(101L, 22L, sameDay));
        when(recordMapper.selectById(102L)).thenReturn(null, buildRecord(102L, 22L, sameDay));

        MigrationResourceImportResult result = handler.importData(mergeContext);
        assertThat(result.recordCount()).isEqualTo(6L);
        ArgumentCaptor<HealthReportDetail> captor = ArgumentCaptor.forClass(HealthReportDetail.class);
        verify(detailMapper, times(2)).insertDetail(captor.capture());
        assertThat(captor.getAllValues().get(0).getRevision()).isEqualTo(1);
        assertThat(captor.getAllValues().get(0).getRecordId()).isEqualTo(101L);
        assertThat(captor.getAllValues().get(1).getItems()).isEmpty();
    }

    @Test
    void shouldRejectAmbiguousSameDayMatchWhenTargetAlreadyHasRecord() throws Exception {
        LocalDate sameDay = LocalDate.of(2026, 5, 10);
        HealthRecord r1 = buildRecord(101L, 11L, sameDay);
        HealthRecord r2 = buildRecord(102L, 11L, sameDay);
        HealthRecord existingTarget = buildRecord(999L, 22L, sameDay);

        HealthRecordMigrationPayload payload = new HealthRecordMigrationPayload();
        payload.setRecords(List.of(r1, r2));

        when(archiveService.readJson(any(), anyString(), eq(HealthRecordMigrationPayload.class))).thenReturn(payload);
        when(recordMapper.selectList(any())).thenReturn(List.of(existingTarget));

        assertThatThrownBy(() -> handler.importData(mergeContext))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATA_MIGRATION_HEALTH_RECORD_MATCH_CONFLICT");
    }

    @Test
    void shouldRespectTimestampAndStrictModeOnExistingReportDetail() throws Exception {
        LocalDate day = LocalDate.of(2026, 5, 10);
        HealthReport rep = buildReport(201L, 11L, day, "年度体检");
        HealthReport existingRep = buildReport(201L, 22L, day, "年度体检");

        HealthReportDetail existingDetail = new HealthReportDetail();
        existingDetail.setReportId(201L);
        existingDetail.setRevision(3);
        existingDetail.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));

        // 1) 同时间或更早时间保留目标
        HealthReportDetailMigrationItem older = new HealthReportDetailMigrationItem(
                201L, null, List.of(), LocalDateTime.of(2026, 5, 10, 12, 0));
        HealthRecordMigrationPayload payload = new HealthRecordMigrationPayload();
        payload.setReports(List.of(rep));
        payload.setReportDetails(List.of(older));

        when(archiveService.readJson(any(), anyString(), eq(HealthRecordMigrationPayload.class))).thenReturn(payload);
        when(reportMapper.selectList(any())).thenReturn(List.of(existingRep));
        when(reportMapper.selectById(201L)).thenReturn(existingRep);
        when(detailMapper.findByReportId(201L)).thenReturn(existingDetail);

        handler.importData(mergeContext);
        verify(detailMapper, never()).updateDetailWithRevision(any(), anyInt());

        // 2) 来源更新更晚时整组替换并加版本
        HealthReportDetailMigrationItem newer = new HealthReportDetailMigrationItem(
                201L, null, List.of(), LocalDateTime.of(2026, 5, 10, 13, 0));
        payload.setReportDetails(List.of(newer));
        handler.importData(mergeContext);
        ArgumentCaptor<HealthReportDetail> captor = ArgumentCaptor.forClass(HealthReportDetail.class);
        verify(detailMapper).updateDetailWithRevision(captor.capture(), eq(3));
        assertThat(captor.getValue().getRevision()).isEqualTo(4);

        // 3) 严格模式报冲突
        ImportContext strictContext = new ImportContext("STRICT", false, Path.of("backup"));
        strictContext.mapUserId(11L, 22L);
        assertThatThrownBy(() -> handler.importData(strictContext))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATA_MIGRATION_HEALTH_REPORT_CONFLICT");
    }

    @Test
    void shouldRejectDuplicateReportDetailAndMissingMapping() throws Exception {
        HealthReportDetailMigrationItem missing = new HealthReportDetailMigrationItem(
                999L, null, List.of(), LocalDateTime.now());
        HealthRecordMigrationPayload payload = new HealthRecordMigrationPayload();
        payload.setReportDetails(List.of(missing));

        when(archiveService.readJson(any(), anyString(), eq(HealthRecordMigrationPayload.class))).thenReturn(payload);
        assertThatThrownBy(() -> handler.importData(mergeContext))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATA_MIGRATION_HEALTH_REPORT_DETAIL_MAPPING_MISSING");

        payload.setReportDetails(List.of(missing, missing));
        assertThatThrownBy(() -> handler.importData(mergeContext))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATA_MIGRATION_HEALTH_REPORT_DETAIL_DUPLICATE");
    }

    private HealthRecord buildRecord(Long id, Long ownerUserId, LocalDate date) {
        HealthRecord rec = new HealthRecord();
        rec.setId(id);
        rec.setOwnerUserId(ownerUserId);
        rec.setMeasureDate(date);
        rec.setWeightKg(new BigDecimal("72.5"));
        rec.setCreatedAt(LocalDateTime.of(2026, 5, 10, 8, 0));
        rec.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 8, 0));
        return rec;
    }

    private HealthReport buildReport(Long id, Long ownerUserId, LocalDate date, String title) {
        HealthReport rep = new HealthReport();
        rep.setId(id);
        rep.setOwnerUserId(ownerUserId);
        rep.setExamDate(date);
        rep.setReportTitle(title);
        rep.setCreatedAt(LocalDateTime.of(2026, 5, 10, 8, 0));
        rep.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 8, 0));
        return rep;
    }

    private HealthReportDetailItem buildItem(String group, String code, String name, String type, String text) {
        HealthReportDetailItem item = new HealthReportDetailItem();
        item.setGroup(group);
        item.setItemCode(code);
        item.setName(name);
        item.setResultType(type);
        item.setResultText(text);
        item.setAbnormalFlag("UNSPECIFIED");
        return item;
    }
}
