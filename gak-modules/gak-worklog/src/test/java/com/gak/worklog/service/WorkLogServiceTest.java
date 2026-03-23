package com.gak.worklog.service;

import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.worklog.dto.CreateWorkLogRequest;
import com.gak.worklog.dto.WeeklyWorkLogBriefResponse;
import com.gak.worklog.dto.WorkLogResponse;
import com.gak.worklog.entity.WorkLog;
import com.gak.worklog.entity.WorkLogType;
import com.gak.worklog.mapper.WorkLogMapper;
import com.gak.worklog.mapper.WorkLogTypeMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class WorkLogServiceTest {

    @Mock
    private WorkLogMapper workLogMapper;

    @Mock
    private WorkLogTypeMapper workLogTypeMapper;

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @InjectMocks
    private WorkLogService workLogService;

    @BeforeEach
    void setUp() {
        lenient().when(dataDictionaryUsageSupport.normalizeMultiValueByUsage(
                eq("APP_WORK_LOG"),
                eq("WORK_LOG"),
                eq("typeCodes"),
                any(),
                eq(true)
        )).thenAnswer(invocation -> normalizeTypeCodes(invocation.getArgument(3)));
        lenient().when(dataDictionaryUsageSupport.normalizeValueByUsage(
                eq("APP_WORK_LOG"),
                eq("WORK_LOG"),
                anyString(),
                any(),
                anyBoolean()
        )).thenAnswer(invocation -> normalizeField(
                invocation.getArgument(2),
                (String) invocation.getArgument(3),
                invocation.getArgument(4)
        ));
    }

    @Test
    void createShouldComputeWorkdayOvertimeFromOffWorkTimeAndNormalizeDictionaryFields() {
        when(workLogMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1001L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 23));
        request.setTypeCodes(List.of("NORMAL", "BUSINESS_TRIP", "NORMAL"));
        request.setLocation("上海办公室");
        request.setProjectCode("GAK");
        request.setOffWorkTime(LocalTime.of(20, 30));
        request.setOvertimeHours(decimal("1.0"));

        WorkLogResponse response = workLogService.create(request);

        ArgumentCaptor<WorkLog> captor = ArgumentCaptor.forClass(WorkLog.class);
        verify(workLogMapper).insert(captor.capture());
        verify(workLogTypeMapper, times(2)).insert(any(WorkLogType.class));
        assertEquals("上海办公室", captor.getValue().getLocation());
        assertEquals("GAK", captor.getValue().getProjectCode());
        assertEquals(decimal("2.5"), captor.getValue().getOvertimeHours());
        assertEquals(LocalTime.of(20, 30), captor.getValue().getOffWorkTime());
        assertEquals(decimal("2.5"), response.getOvertimeHours());
        assertEquals(LocalTime.of(20, 30), response.getOffWorkTime());
        assertEquals(List.of("NORMAL", "BUSINESS_TRIP"), response.getTypeCodes());
    }

    @Test
    void createShouldFallbackToLegacyWorkdayOvertimeWhenOffWorkTimeMissing() {
        when(workLogMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1002L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 24));
        request.setLocation("上海办公室");
        request.setProjectCode("GAK");
        request.setOvertimeHours(decimal("1.5"));

        WorkLogResponse response = workLogService.create(request);

        assertEquals(decimal("1.5"), response.getOvertimeHours());
        assertEquals(null, response.getOffWorkTime());
    }

    @Test
    void createShouldUseManualWeekendOvertime() {
        when(workLogMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1003L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 29));
        request.setLocation("居家");
        request.setProjectCode("GAK");
        request.setOvertimeHours(decimal("4.0"));

        WorkLogResponse response = workLogService.create(request);

        assertEquals(decimal("4.0"), response.getOvertimeHours());
        assertEquals(null, response.getOffWorkTime());
    }

    @Test
    void createShouldRejectInvalidProjectCode() {
        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 23));
        request.setLocation("上海办公室");
        request.setProjectCode("UNKNOWN");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> workLogService.create(request));
        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("projectCode 非法", exception.getReason());
    }

    @Test
    void listWeeklyBriefShouldIncludeLocationOffWorkTimeAndRemark() {
        when(workLogMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(buildWorkLog())));
        when(workLogTypeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(buildType(2001L, "NORMAL"))));

        List<WeeklyWorkLogBriefResponse> result = workLogService.listWeeklyBrief(1L, LocalDate.of(2026, 3, 23));

        assertEquals(1, result.size());
        assertEquals("上海办公室", result.get(0).getLocation());
        assertEquals(LocalTime.of(20, 0), result.get(0).getOffWorkTime());
        assertEquals("继续验证", result.get(0).getRemark());
        assertEquals("完成工作日志改版", result.get(0).getBrief());
    }

    private CreateWorkLogRequest buildBaseRequest(LocalDate logDate) {
        CreateWorkLogRequest request = new CreateWorkLogRequest();
        request.setUserId(1L);
        request.setLogDate(logDate);
        request.setTypeCodes(List.of("NORMAL"));
        request.setWorkItem("完成工作日志改版");
        request.setPersonDay(decimal("1.0"));
        request.setRemark("继续验证");
        return request;
    }

    private WorkLog buildWorkLog() {
        WorkLog workLog = new WorkLog();
        workLog.setId(2001L);
        workLog.setUserId(1L);
        workLog.setLogDate(LocalDate.of(2026, 3, 23));
        workLog.setLocation("上海办公室");
        workLog.setProjectCode("GAK");
        workLog.setContent("完成工作日志改版\n补充周报字段");
        workLog.setPersonDay(decimal("1.0"));
        workLog.setOvertimeHours(decimal("2.0"));
        workLog.setOffWorkTime(LocalTime.of(20, 0));
        workLog.setRemark("继续验证");
        workLog.setCreatedAt(LocalDateTime.now());
        workLog.setUpdatedAt(LocalDateTime.now());
        return workLog;
    }

    private WorkLogType buildType(Long workLogId, String typeCode) {
        WorkLogType type = new WorkLogType();
        type.setWorkLogId(workLogId);
        type.setTypeCode(typeCode);
        type.setCreatedAt(LocalDateTime.now());
        return type;
    }

    private List<String> normalizeTypeCodes(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException("DICT_MULTI_VALUE_REQUIRED", "字典多选值不能为空");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(normalizeSingle(value, List.of("NORMAL", "BUSINESS_TRIP", "LEAVE"), true));
        }
        return new ArrayList<>(result);
    }

    private String normalizeField(String field, String value, boolean required) {
        return switch (field) {
            case "typeCodes" -> normalizeSingle(value, List.of("NORMAL", "BUSINESS_TRIP", "LEAVE"), required);
            case "projectCode" -> normalizeSingle(value, List.of("GAK", "CLIENT", "OPS"), required);
            case "location" -> normalizeSingle(value, List.of("上海办公室", "深圳办公室", "居家", "客户现场", "出差在途"), required);
            default -> value;
        };
    }

    private String normalizeSingle(String value, List<String> options, boolean required) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            if (required) {
                throw new BusinessException("DICT_ITEM_VALUE_REQUIRED", "字典值不能为空");
            }
            return null;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(normalized)) {
                return option;
            }
        }
        throw new BusinessException("DICT_ITEM_VALUE_INVALID", "字典值非法");
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
