package com.gak.healthrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.attachment.service.AttachmentService;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthReportDetail;
import com.gak.healthrecord.dto.HealthReportDetailItemRequest;
import com.gak.healthrecord.dto.SaveHealthReportDetailsRequest;
import com.gak.healthrecord.mapper.HealthRecordMapper;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.mapper.HealthReportMapper;
import com.gak.healthrecord.vo.HealthReportDetailsVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * 体检报告明细服务测试。
 */
@ExtendWith(MockitoExtension.class)
class HealthReportDetailServiceTest {

    @Mock
    private HealthReportMapper healthReportMapper;

    @Mock
    private HealthRecordMapper healthRecordMapper;

    @Mock
    private HealthReportDetailMapper healthReportDetailMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AttachmentService attachmentService;

    private HealthReportDetailService service;

    @BeforeEach
    void setUp() {
        service = new HealthReportDetailService(
                healthReportMapper,
                healthRecordMapper,
                healthReportDetailMapper,
                userMapper,
                attachmentService
        );
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void shouldReturnEmptyDetailsWithRevisionZeroWhenNotPersisted() {
        HealthReport report = buildReport(10L, 1L);
        when(healthReportMapper.selectById(10L)).thenReturn(report);
        when(healthReportDetailMapper.findByReportId(10L)).thenReturn(null);
        when(attachmentService.listBusinessAttachments(any(), eq(10L), any())).thenReturn(List.of());

        HealthReportDetailsVO result = service.getDetails(1L, 10L);
        assertThat(result.getRevision()).isEqualTo(0);
        assertThat(result.getLinkedRecordId()).isNull();
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getReport().getId()).isEqualTo(10L);
    }

    @Test
    void shouldSaveDetailsDeriveNumericValueAndIncrementRevision() {
        HealthReport report = buildReport(10L, 1L);
        HealthRecord record = buildRecord(20L, 1L);
        when(healthReportMapper.selectOne(any())).thenReturn(report);
        when(healthRecordMapper.selectById(20L)).thenReturn(record);
        when(healthReportDetailMapper.findByReportId(10L)).thenReturn(null);
        when(healthReportDetailMapper.findByRecordId(20L)).thenReturn(null);
        when(attachmentService.listBusinessAttachments(any(), eq(10L), any())).thenReturn(List.of());

        HealthReportDetailItemRequest numItem = new HealthReportDetailItemRequest();
        numItem.setGroup("URINALYSIS");
        numItem.setItemCode("urineSpecificGravity");
        numItem.setName("尿比重");
        numItem.setResultType("NUMBER");
        numItem.setResultText(" 0.000 ");
        numItem.setReferenceText("1.015–1.025");
        numItem.setAbnormalFlag("LOW");
        numItem.setSourcePage(3);

        HealthReportDetailItemRequest qualItem = new HealthReportDetailItemRequest();
        qualItem.setGroup("URINALYSIS");
        qualItem.setItemCode("urineProtein");
        qualItem.setName("尿蛋白");
        qualItem.setResultType("QUALITATIVE");
        qualItem.setResultText("阴性");

        HealthReportDetailItemRequest textItem = new HealthReportDetailItemRequest();
        textItem.setGroup("IMAGING");
        textItem.setItemCode("renalCyst");
        textItem.setName("右肾囊肿");
        textItem.setResultType("TEXT");
        textItem.setResultText("<0.05");

        SaveHealthReportDetailsRequest request = new SaveHealthReportDetailsRequest();
        request.setExpectedRevision(0);
        request.setLinkedRecordId(20L);
        request.setItems(List.of(numItem, qualItem, textItem));

        HealthReportDetailsVO saved = service.saveDetails(1L, 10L, request);
        verify(healthReportDetailMapper).insertDetail(any(HealthReportDetail.class));
        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getLinkedRecordId()).isEqualTo(20L);
        assertThat(saved.getReport().getLinkedRecordId()).isEqualTo(20L);
        assertThat(saved.getItems()).hasSize(3);
        assertThat(saved.getItems().get(0).getResultText()).isEqualTo("0.000");
        assertThat(saved.getItems().get(0).getNumericValue()).isEqualTo("0.000");
        assertThat(saved.getItems().get(1).getNumericValue()).isNull();
        assertThat(saved.getItems().get(1).getAbnormalFlag()).isEqualTo("UNSPECIFIED");
        assertThat(saved.getItems().get(2).getResultText()).isEqualTo("<0.05");
        assertThat(saved.getItems().get(2).getNumericValue()).isNull();
    }

    @Test
    void shouldRejectMissingLinkedRecordIdKeyAndInvalidItems() {
        SaveHealthReportDetailsRequest missingKey = new SaveHealthReportDetailsRequest();
        missingKey.setExpectedRevision(0);
        missingKey.setItems(List.of());
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, missingKey))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "HEALTH_REPORT_DETAIL_INVALID");

        HealthReport report = buildReport(10L, 1L);
        when(healthReportMapper.selectOne(any())).thenReturn(report);

        // 未知项目代码
        HealthReportDetailItemRequest unknownCode = new HealthReportDetailItemRequest();
        unknownCode.setGroup("URINALYSIS");
        unknownCode.setItemCode("notExists");
        unknownCode.setName("测试");
        unknownCode.setResultType("TEXT");
        unknownCode.setResultText("正常");
        SaveHealthReportDetailsRequest req1 = buildSaveRequest(0, null, List.of(unknownCode));
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, req1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目代码不存在");

        // 分组与项目代码不匹配
        HealthReportDetailItemRequest mismatchGroup = new HealthReportDetailItemRequest();
        mismatchGroup.setGroup("LIVER");
        mismatchGroup.setItemCode("urineSpecificGravity");
        mismatchGroup.setName("尿比重");
        mismatchGroup.setResultType("NUMBER");
        mismatchGroup.setResultText("1.012");
        SaveHealthReportDetailsRequest req2 = buildSaveRequest(0, null, List.of(mismatchGroup));
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, req2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");

        // 非法数值格式（含比较符不能用 NUMBER）
        HealthReportDetailItemRequest badNumber = new HealthReportDetailItemRequest();
        badNumber.setGroup("OTHER");
        badNumber.setName("自定义");
        badNumber.setResultType("NUMBER");
        badNumber.setResultText("<0.05");
        SaveHealthReportDetailsRequest req3 = buildSaveRequest(0, null, List.of(badNumber));
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, req3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数值结果格式非法");

        // 超过 200 项
        List<HealthReportDetailItemRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            HealthReportDetailItemRequest item = new HealthReportDetailItemRequest();
            item.setGroup("OTHER");
            item.setName("项" + i);
            item.setResultType("TEXT");
            item.setResultText("正常");
            tooMany.add(item);
        }
        SaveHealthReportDetailsRequest req4 = buildSaveRequest(0, null, tooMany);
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, req4))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    void shouldRejectStaleRevisionAndAlreadyLinkedRecordAndCrossAccount() {
        HealthReport report = buildReport(10L, 1L);
        when(healthReportMapper.selectOne(any())).thenReturn(report);

        HealthReportDetail existing = new HealthReportDetail();
        existing.setReportId(10L);
        existing.setRevision(2);
        when(healthReportDetailMapper.findByReportId(10L)).thenReturn(existing);

        SaveHealthReportDetailsRequest stale = buildSaveRequest(1, null, List.of());
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, stale))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "HEALTH_REPORT_DETAIL_CONFLICT");

        HealthRecord ownRecord = buildRecord(20L, 1L);
        when(healthRecordMapper.selectById(20L)).thenReturn(ownRecord);
        HealthReportDetail otherLink = new HealthReportDetail();
        otherLink.setReportId(99L);
        otherLink.setRecordId(20L);
        when(healthReportDetailMapper.findByRecordId(20L)).thenReturn(otherLink);

        SaveHealthReportDetailsRequest conflictLink = buildSaveRequest(2, 20L, List.of());
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, conflictLink))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "HEALTH_RECORD_ALREADY_LINKED");

        // 跨账号记录返回 404
        HealthRecord otherUserRecord = buildRecord(30L, 2L);
        when(healthRecordMapper.selectById(30L)).thenReturn(otherUserRecord);
        SaveHealthReportDetailsRequest crossOwner = buildSaveRequest(2, 30L, List.of());
        assertThatThrownBy(() -> service.saveDetails(1L, 10L, crossOwner))
                .isInstanceOf(ResponseStatusException.class);
    }

    private SaveHealthReportDetailsRequest buildSaveRequest(int expectedRevision, Long linkedRecordId,
                                                            List<HealthReportDetailItemRequest> items) {
        SaveHealthReportDetailsRequest request = new SaveHealthReportDetailsRequest();
        request.setExpectedRevision(expectedRevision);
        request.setLinkedRecordId(linkedRecordId);
        request.setItems(items);
        return request;
    }

    private HealthReport buildReport(Long id, Long ownerUserId) {
        HealthReport report = new HealthReport();
        report.setId(id);
        report.setOwnerUserId(ownerUserId);
        report.setExamDate(LocalDate.of(2026, 5, 10));
        report.setReportTitle("2026年度体检");
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    private HealthRecord buildRecord(Long id, Long ownerUserId) {
        HealthRecord record = new HealthRecord();
        record.setId(id);
        record.setOwnerUserId(ownerUserId);
        record.setMeasureDate(LocalDate.of(2026, 5, 10));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
