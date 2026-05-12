package com.gak.healthrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.dto.HealthTrendQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthSummaryVO;
import com.gak.healthrecord.vo.HealthTrendVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private UserMapper userMapper;

    private HealthRecordService healthRecordService;

    @BeforeEach
    void setUp() {
        healthRecordService = new HealthRecordService(
                healthRecordMapper,
                healthVisitMapper,
                healthReportMapper,
                userMapper,
                "./target/health-records-test",
                "/api/health-records/report-files/"
        );
        User user = new User();
        user.setId(1L);
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
    void shouldReturnTrendPointsForMetric() {
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(
                buildRecord(101L, LocalDate.of(2026, 5, 8)),
                buildRecord(102L, LocalDate.of(2026, 5, 6))
        ));

        HealthTrendQueryRequest request = new HealthTrendQueryRequest();
        request.setMetricKey("weightKg");
        request.setLimit(12);

        HealthTrendVO trend = healthRecordService.getTrends(1L, request);
        assertThat(trend.getMetricKey()).isEqualTo("weightKg");
        assertThat(trend.getPoints()).hasSize(2);
        assertThat(trend.getPoints().get(0).getMeasureDate()).isEqualTo(LocalDate.of(2026, 5, 6));
    }

    @Test
    void shouldCreateRecordWithScaledValues() {
        SaveHealthRecordRequest request = new SaveHealthRecordRequest();
        request.setMeasureDate(LocalDate.of(2026, 5, 10));
        request.setWeightKg(new BigDecimal("73.26"));
        request.setBodyFatRate(new BigDecimal("19.24"));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenAnswer(invocation -> {
            HealthRecord record = invocation.getArgument(0);
            record.setId(101L);
            return 1;
        });

        HealthRecordVO result = healthRecordService.createRecord(1L, request);
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getWeightKg()).isEqualByComparingTo("73.3");
        assertThat(result.getBodyFatRate()).isEqualByComparingTo("19.2");
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
