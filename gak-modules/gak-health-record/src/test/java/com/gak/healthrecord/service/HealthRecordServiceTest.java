package com.gak.healthrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.dto.HealthRecordQueryRequest;
import com.gak.healthrecord.dto.HealthReportQueryRequest;
import com.gak.healthrecord.dto.HealthTrendQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthReportFileResource;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.healthrecord.vo.HealthSummaryVO;
import com.gak.healthrecord.vo.HealthTrendVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 健康服务测试。
 */
@ExtendWith(MockitoExtension.class)
class HealthRecordServiceTest {

    @Mock
    private HealthRecordMapper healthRecordMapper;

    @Mock
    private HealthVisitMapper healthVisitMapper;

    @Mock
    private HealthReportMapper healthReportMapper;

    @Mock
    private HealthReportDetailMapper healthReportDetailMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private HealthVisitBillSyncService visitBillSyncService;

    private HealthRecordService healthRecordService;

    @BeforeEach
    void setUp() {
        healthRecordService = new HealthRecordService(
                healthRecordMapper,
                healthVisitMapper,
                healthReportMapper,
                healthReportDetailMapper,
                userMapper,
                attachmentService,
                visitBillSyncService,
                "./target/health-records-test",
                "/api/health-records/report-files/"
        );
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);
        user.setStatus("ENABLED");
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void shouldBuildSummaryFromRecordsVisitsAndReports() {
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(buildRecord(101L, LocalDate.of(2026, 5, 8))));
        when(healthVisitMapper.selectList(any())).thenReturn(List.of(buildVisit(201L, LocalDate.of(2026, 5, 9))));
        when(healthReportMapper.selectList(any())).thenReturn(List.of(buildReport(301L, LocalDate.of(2026, 5, 7))));

        HealthSummaryVO summary = healthRecordService.getSummary(1L);
        assertThat(summary.getLatestMeasureDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(summary.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(summary.getLastExamDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(summary.getRecordCount()).isEqualTo(1);
        assertThat(summary.getVisitCount()).isEqualTo(1);
        assertThat(summary.getReportCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnTrendPointsForMetricWithRecordIdAndSkipNullsKeepingZeroAndDuplicates() {
        List<HealthRecord> records = new ArrayList<>();
        // 模拟倒序查询结果：共 14 条，其中 1 条为空、2 条同日同值、1 条为 0
        LocalDate baseDate = LocalDate.of(2026, 5, 1);
        for (int i = 13; i >= 0; i--) {
            HealthRecord rec = buildRecord(1000L + i, i == 5 ? baseDate.plusDays(4) : baseDate.plusDays(i));
            if (i == 7) {
                rec.setTriglycerides(null);
            } else if (i == 2) {
                rec.setTriglycerides(BigDecimal.ZERO);
            } else {
                rec.setTriglycerides(new BigDecimal("1.25"));
            }
            records.add(rec);
        }
        when(healthRecordMapper.selectList(any())).thenReturn(records);

        HealthTrendQueryRequest request = new HealthTrendQueryRequest();
        request.setMetricKey("triglycerides");
        request.setLimit(12);

        HealthTrendVO trend = healthRecordService.getTrends(1L, request);
        assertThat(trend.getMetricKey()).isEqualTo("triglycerides");
        assertThat(trend.getPoints()).hasSize(12);
        assertThat(trend.getPoints().get(0).getRecordId()).isEqualTo(1001L);
        assertThat(trend.getPoints().stream().anyMatch(p -> p.getRecordId().equals(1007L))).isFalse();
        assertThat(trend.getPoints().stream().filter(p -> p.getRecordId().equals(1002L)).findFirst().orElseThrow().getValue())
                .isEqualByComparingTo("0");
        assertThat(trend.getPoints().stream().filter(p -> p.getMeasureDate().equals(baseDate.plusDays(4))).count())
                .isEqualTo(2);
    }

    @Test
    void shouldCalculateBmiInRecordAndTrendAndSkipInvalidHeightOrWeight() {
        HealthRecord valid = buildRecord(201L, LocalDate.of(2026, 5, 10));
        valid.setHeightCm(new BigDecimal("176.0"));
        valid.setWeightKg(new BigDecimal("78.0"));

        HealthRecord zeroHeight = buildRecord(202L, LocalDate.of(2026, 5, 9));
        zeroHeight.setHeightCm(BigDecimal.ZERO);
        zeroHeight.setWeightKg(new BigDecimal("78.0"));

        HealthRecord missingWeight = buildRecord(203L, LocalDate.of(2026, 5, 8));
        missingWeight.setHeightCm(new BigDecimal("176.0"));
        missingWeight.setWeightKg(null);

        when(healthRecordMapper.selectById(201L)).thenReturn(valid);
        when(healthRecordMapper.selectById(202L)).thenReturn(zeroHeight);
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(valid, zeroHeight, missingWeight));

        assertThat(healthRecordService.getRecord(1L, 201L).getBmi()).isEqualByComparingTo("25.18");
        assertThat(healthRecordService.getRecord(1L, 202L).getBmi()).isNull();

        HealthTrendQueryRequest request = new HealthTrendQueryRequest();
        request.setMetricKey("bmi");
        request.setLimit(12);
        HealthTrendVO trend = healthRecordService.getTrends(1L, request);
        assertThat(trend.getPoints()).hasSize(1);
        assertThat(trend.getPoints().get(0).getRecordId()).isEqualTo(201L);
        assertThat(trend.getPoints().get(0).getValue()).isEqualByComparingTo("25.18");
    }

    @Test
    void shouldSupportHdlLdlAstGgtTrendKeysAndRejectInvalidKey() {
        HealthRecord rec = buildRecord(301L, LocalDate.of(2026, 5, 10));
        rec.setHdlCholesterol(new BigDecimal("1.35"));
        rec.setLdlCholesterol(new BigDecimal("2.80"));
        rec.setAspartateAminotransferase(24);
        rec.setGammaGlutamylTransferase(31);
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(rec));

        for (String key : List.of("hdlCholesterol", "ldlCholesterol", "aspartateAminotransferase", "gammaGlutamylTransferase")) {
            HealthTrendQueryRequest req = new HealthTrendQueryRequest();
            req.setMetricKey(key);
            assertThat(healthRecordService.getTrends(1L, req).getPoints()).hasSize(1);
        }

        HealthTrendQueryRequest invalid = new HealthTrendQueryRequest();
        invalid.setMetricKey("unknownKey");
        assertThatThrownBy(() -> healthRecordService.getTrends(1L, invalid))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "HEALTH_METRIC_KEY_INVALID");
    }

    @Test
    void shouldReturnDualValueBloodPressureTrendAndIosMetricTrends() {
        HealthRecord rec = buildRecord(401L, LocalDate.of(2026, 11, 14));
        rec.setHeightCm(new BigDecimal("176.0"));
        rec.setSystolicPressure(119);
        rec.setDiastolicPressure(81);
        rec.setHeartRate(62);
        rec.setWaistCm(new BigDecimal("91.5"));
        rec.setSteps(8640);
        rec.setSleepHours(new BigDecimal("7.2"));
        rec.setBloodOxygen(new BigDecimal("98.2"));
        rec.setHrvMs(48);
        rec.setVo2Max(new BigDecimal("41.5"));
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(rec));

        HealthTrendQueryRequest bpReq = new HealthTrendQueryRequest();
        bpReq.setMetricKey("bloodPressure");
        HealthTrendVO bpTrend = healthRecordService.getTrends(1L, bpReq);
        assertThat(bpTrend.getPoints()).hasSize(1);
        assertThat(bpTrend.getPoints().get(0).getValue()).isEqualByComparingTo("119");
        assertThat(bpTrend.getPoints().get(0).getSecondaryValue()).isEqualByComparingTo("81");

        for (String key : List.of("heightCm", "heartRate", "waistCm", "steps", "sleepHours", "bloodOxygen", "hrvMs", "vo2Max")) {
            HealthTrendQueryRequest req = new HealthTrendQueryRequest();
            req.setMetricKey(key);
            assertThat(healthRecordService.getTrends(1L, req).getPoints()).hasSize(1);
        }
    }

    @Test
    void shouldCreateRecordWithScaledValuesAndBmi() {
        SaveHealthRecordRequest request = new SaveHealthRecordRequest();
        request.setMeasureDate(LocalDate.of(2026, 5, 10));
        request.setHeightCm(new BigDecimal("176.0"));
        request.setWeightKg(new BigDecimal("78.0"));
        request.setBodyFatRate(new BigDecimal("19.24"));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenAnswer(invocation -> {
            HealthRecord record = invocation.getArgument(0);
            record.setId(101L);
            return 1;
        });

        HealthRecordVO result = healthRecordService.createRecord(1L, request);
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getWeightKg()).isEqualByComparingTo("78.0");
        assertThat(result.getBodyFatRate()).isEqualByComparingTo("19.2");
        assertThat(result.getBmi()).isEqualByComparingTo("25.18");
    }

    @Test
    void shouldPopulateSourceReportIdAndLinkedRecordIdInBatchPages() {
        HealthRecord rec = buildRecord(101L, LocalDate.of(2026, 5, 10));
        HealthReport rep = buildReport(301L, LocalDate.of(2026, 5, 10));
        HealthReportDetail link = new HealthReportDetail();
        link.setReportId(301L);
        link.setRecordId(101L);

        when(healthRecordMapper.selectList(any())).thenReturn(List.of(rec));
        when(healthReportDetailMapper.findLinksByRecordIds(List.of(101L))).thenReturn(List.of(link));
        when(healthReportMapper.selectList(any())).thenReturn(List.of(rep));
        when(healthReportDetailMapper.findLinksByReportIds(List.of(301L))).thenReturn(List.of(link));
        when(attachmentService.listBusinessAttachments(any(), any(), any())).thenReturn(List.of());

        HealthRecordQueryRequest recQuery = new HealthRecordQueryRequest();
        recQuery.setMeasureDate(LocalDate.of(2026, 5, 10));
        PagedResult<HealthRecordVO> recPage = healthRecordService.pageRecords(1L, recQuery);
        assertThat(recPage.list()).hasSize(1);
        assertThat(recPage.list().get(0).getSourceReportId()).isEqualTo(301L);

        PagedResult<HealthReportVO> repPage = healthRecordService.pageReports(1L, new HealthReportQueryRequest());
        assertThat(repPage.list()).hasSize(1);
        assertThat(repPage.list().get(0).getLinkedRecordId()).isEqualTo(101L);
    }

    @Test
    void shouldRejectDeletingLinkedHealthRecord() {
        HealthRecord rec = buildRecord(101L, LocalDate.of(2026, 5, 10));
        HealthReportDetail link = new HealthReportDetail();
        link.setReportId(301L);
        link.setRecordId(101L);
        when(healthRecordMapper.selectById(101L)).thenReturn(rec);
        when(healthReportDetailMapper.findByRecordId(101L)).thenReturn(link);

        assertThatThrownBy(() -> healthRecordService.deleteRecord(1L, 101L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "HEALTH_RECORD_LINKED");
        verify(healthRecordMapper, never()).deleteById(101L);
    }

    @Test
    void loadReportFileShouldEnforceAppPermissionAndOwnership() throws Exception {
        Path dir = Path.of("./target/health-records-test");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("owned.pdf"), "pdf-content", StandardCharsets.UTF_8);

        when(healthReportMapper.hasHealthAppAccess(1L)).thenReturn(false);
        assertThatThrownBy(() -> healthRecordService.loadReportFile(1L, "owned.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        when(healthReportMapper.hasHealthAppAccess(1L)).thenReturn(true);
        when(healthReportMapper.existsOwnedReportFile(1L, "owned.pdf", "/api/health-records/report-files/owned.pdf"))
                .thenReturn(false);
        when(healthVisitMapper.existsOwnedCaseRecordFile(1L, "owned.pdf", "/api/health-records/report-files/owned.pdf"))
                .thenReturn(false);
        assertThatThrownBy(() -> healthRecordService.loadReportFile(1L, "owned.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(healthReportMapper.existsOwnedReportFile(1L, "owned.pdf", "/api/health-records/report-files/owned.pdf"))
                .thenReturn(true);
        HealthReportFileResource fileResource = healthRecordService.loadReportFile(1L, "owned.pdf");
        assertThat(fileResource.fileName()).isEqualTo("owned.pdf");
        assertThat(fileResource.resource().exists()).isTrue();
    }

    private HealthRecord buildRecord(Long id, LocalDate date) {
        HealthRecord record = new HealthRecord();
        record.setId(id);
        record.setOwnerUserId(1L);
        record.setMeasureDate(date);
        record.setWeightKg(new BigDecimal("72.8"));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private HealthVisit buildVisit(Long id, LocalDate date) {
        HealthVisit visit = new HealthVisit();
        visit.setId(id);
        visit.setOwnerUserId(1L);
        visit.setVisitDate(date);
        visit.setHospitalName("市人民医院");
        visit.setCreatedAt(LocalDateTime.now());
        visit.setUpdatedAt(LocalDateTime.now());
        return visit;
    }

    private HealthReport buildReport(Long id, LocalDate date) {
        HealthReport report = new HealthReport();
        report.setId(id);
        report.setOwnerUserId(1L);
        report.setExamDate(date);
        report.setReportTitle("体检报告");
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }
}
