package com.gak.worklog.service;

import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.worklog.dto.CreateWorkLogRequest;
import com.gak.worklog.dto.UpdateWorkLogRequest;
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

        WorkLogResponse response = workLogService.create(1L, request);

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
    void createShouldUseCurrentUserIdInsteadOfRequestUserId() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1004L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 25));
        request.setUserId(null);

        workLogService.create(1L, request);

        ArgumentCaptor<WorkLog> captor = ArgumentCaptor.forClass(WorkLog.class);
        verify(workLogMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void createShouldFallbackToLegacyWorkdayOvertimeWhenOffWorkTimeMissing() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1002L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 24));
        request.setLocation("上海办公室");
        request.setProjectCode("GAK");
        request.setOvertimeHours(decimal("1.5"));

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals(decimal("1.5"), response.getOvertimeHours());
        assertEquals(null, response.getOffWorkTime());
    }

    @Test
    void createShouldUseManualWeekendOvertime() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1003L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 29));
        request.setLocation("居家");
        request.setProjectCode("GAK");
        request.setOvertimeHours(decimal("4.0"));

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals(decimal("4.0"), response.getOvertimeHours());
        assertEquals(null, response.getOffWorkTime());
    }

    @Test
    void createShouldCalculateCityBusinessTripAllowance() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1005L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 30));
        request.setTypeCodes(List.of("CITY_BUSINESS_TRIP"));
        request.setBusinessTripReimbursed(true);

        WorkLogResponse response = workLogService.create(1L, request);

        ArgumentCaptor<WorkLog> captor = ArgumentCaptor.forClass(WorkLog.class);
        verify(workLogMapper).insert(captor.capture());
        assertEquals("CITY", captor.getValue().getBusinessTripAllowanceScene());
        assertEquals(decimal("100.00"), captor.getValue().getBusinessTripAllowanceAmount());
        assertEquals(Boolean.TRUE, captor.getValue().getBusinessTripReimbursed());
        assertEquals(decimal("100.00"), response.getBusinessTripAllowanceAmount());
        assertEquals(Boolean.TRUE, response.getBusinessTripReimbursed());
    }

    @Test
    void createShouldCalculateOutOfCityTransitAllowance() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1006L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 31));
        request.setTypeCodes(List.of("OUT_OF_CITY_BUSINESS_TRIP"));
        request.setBusinessTripAllowanceScene("OUT_OF_CITY_TRANSIT");

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals("OUT_OF_CITY_TRANSIT", response.getBusinessTripAllowanceScene());
        assertEquals(decimal("110.00"), response.getBusinessTripAllowanceAmount());
        assertEquals(Boolean.FALSE, response.getBusinessTripReimbursed());
    }

    @Test
    void createShouldClearAllowanceWhenNotBusinessTrip() {
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1007L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 4, 1));
        request.setBusinessTripAllowanceScene("OUT_OF_CITY_TRANSIT");
        request.setBusinessTripReimbursed(true);

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals(null, response.getBusinessTripAllowanceScene());
        assertEquals(decimal("0.00"), response.getBusinessTripAllowanceAmount());
        assertEquals(Boolean.FALSE, response.getBusinessTripReimbursed());
    }

    @Test
    void createShouldRejectMixedCityAndOutOfCityBusinessTripTypes() {
        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 4, 2));
        request.setTypeCodes(List.of("CITY_BUSINESS_TRIP", "OUT_OF_CITY_BUSINESS_TRIP"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> workLogService.create(1L, request));

        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("市内出差和市外出差不能同时选择", exception.getReason());
    }

    @Test
    void createShouldRejectInvalidProjectCode() {
        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 3, 23));
        request.setLocation("上海办公室");
        request.setProjectCode("UNKNOWN");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> workLogService.create(1L, request));
        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("projectCode 非法", exception.getReason());
    }

    @Test
    void createShouldAllowAnotherProjectWhenDailyPersonDayDoesNotExceedOne() {
        when(workLogMapper.countByUserDateAndProject(1L, LocalDate.of(2026, 4, 3), "CLIENT", null))
                .thenReturn(0L);
        when(workLogMapper.sumPersonDayByUserAndDate(1L, LocalDate.of(2026, 4, 3), null))
                .thenReturn(decimal("0.4"));
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1010L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));

        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 4, 3));
        request.setProjectCode("CLIENT");
        request.setPersonDay(decimal("0.6"));

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals("CLIENT", response.getProjectCode());
        assertEquals(decimal("0.6"), response.getPersonDay());
        verify(workLogMapper).lockUserWorkLogs(1L);
    }

    @Test
    void createShouldRejectDuplicateProjectOnSameDate() {
        LocalDate logDate = LocalDate.of(2026, 4, 4);
        when(workLogMapper.countByUserDateAndProject(1L, logDate, "GAK", null)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workLogService.create(1L, buildBaseRequest(logDate))
        );

        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("该用户在当前日期和项目下已存在工作日志", exception.getReason());
    }

    @Test
    void createShouldRejectWhenDailyPersonDayExceedsOne() {
        LocalDate logDate = LocalDate.of(2026, 4, 5);
        when(workLogMapper.countByUserDateAndProject(1L, logDate, "GAK", null)).thenReturn(0L);
        when(workLogMapper.sumPersonDayByUserAndDate(1L, logDate, null)).thenReturn(decimal("0.6"));
        CreateWorkLogRequest request = buildBaseRequest(logDate);
        request.setPersonDay(decimal("0.5"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workLogService.create(1L, request)
        );

        assertEquals(BAD_REQUEST, exception.getStatusCode());
        assertEquals("2026-04-05 当天总人天不能超过 1，当前剩余 0.4 人天", exception.getReason());
    }

    @Test
    void updateShouldExcludeCurrentLogFromDailyPersonDayTotal() {
        LocalDate logDate = LocalDate.of(2026, 3, 23);
        when(workLogMapper.selectById(2001L)).thenReturn(buildWorkLog());
        when(workLogMapper.countByUserDateAndProject(1L, logDate, "GAK", 2001L)).thenReturn(0L);
        when(workLogMapper.sumPersonDayByUserAndDate(1L, logDate, 2001L)).thenReturn(decimal("0.4"));
        UpdateWorkLogRequest request = buildUpdateRequest(logDate);
        request.setPersonDay(decimal("0.6"));

        WorkLogResponse response = workLogService.update(1L, 2001L, request);

        assertEquals(decimal("0.6"), response.getPersonDay());
        verify(workLogMapper).lockUserWorkLogs(1L);
    }

    @Test
    void createShouldAcceptOvertimeAsIndependentType() {
        when(workLogMapper.countByUserDateAndProject(1L, LocalDate.of(2026, 4, 6), "GAK", null))
                .thenReturn(0L);
        doAnswer(invocation -> {
            WorkLog workLog = invocation.getArgument(0);
            workLog.setId(1011L);
            return 1;
        }).when(workLogMapper).insert(any(WorkLog.class));
        CreateWorkLogRequest request = buildBaseRequest(LocalDate.of(2026, 4, 6));
        request.setTypeCodes(List.of("OVERTIME"));
        request.setPersonDay(decimal("0.0"));

        WorkLogResponse response = workLogService.create(1L, request);

        assertEquals(List.of("OVERTIME"), response.getTypeCodes());
    }

    @Test
    void getShouldRejectForeignWorkLog() {
        WorkLog workLog = buildWorkLog();
        workLog.setUserId(2L);
        when(workLogMapper.selectById(2001L)).thenReturn(workLog);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> workLogService.get(1L, 2001L));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void listWeeklyBriefShouldIncludeLocationOffWorkTimeAndRemark() {
        when(workLogMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(buildWorkLog())));
        when(workLogTypeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(buildType(2001L, "NORMAL"))));

        List<WeeklyWorkLogBriefResponse> result = workLogService.listWeeklyBrief(1L, LocalDate.of(2026, 3, 23));

        assertEquals(1, result.size());
        assertEquals("上海办公室", result.get(0).getLocation());
        assertEquals(LocalTime.of(20, 0), result.get(0).getOffWorkTime());
        assertEquals(decimal("0.00"), result.get(0).getBusinessTripAllowanceAmount());
        assertEquals(Boolean.FALSE, result.get(0).getBusinessTripReimbursed());
        assertEquals("继续验证", result.get(0).getRemark());
        assertEquals("完成工作日志改版", result.get(0).getBrief());
    }

    private CreateWorkLogRequest buildBaseRequest(LocalDate logDate) {
        CreateWorkLogRequest request = new CreateWorkLogRequest();
        request.setUserId(1L);
        request.setLogDate(logDate);
        request.setTypeCodes(List.of("NORMAL"));
        request.setLocation("上海办公室");
        request.setProjectCode("GAK");
        request.setWorkItem("完成工作日志改版");
        request.setPersonDay(decimal("1.0"));
        request.setRemark("继续验证");
        return request;
    }

    private UpdateWorkLogRequest buildUpdateRequest(LocalDate logDate) {
        UpdateWorkLogRequest request = new UpdateWorkLogRequest();
        request.setLogDate(logDate);
        request.setTypeCodes(List.of("NORMAL"));
        request.setLocation("上海办公室");
        request.setProjectCode("GAK");
        request.setWorkItem("更新工作日志");
        request.setPersonDay(decimal("1.0"));
        request.setOffWorkTime(LocalTime.of(18, 0));
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
        workLog.setBusinessTripAllowanceScene(null);
        workLog.setBusinessTripAllowanceAmount(decimal("0.00"));
        workLog.setBusinessTripReimbursed(false);
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
            result.add(normalizeSingle(value, workLogTypeValues(), true));
        }
        return new ArrayList<>(result);
    }

    private String normalizeField(String field, String value, boolean required) {
        return switch (field) {
            case "typeCodes" -> normalizeSingle(value, workLogTypeValues(), required);
            case "projectCode" -> normalizeSingle(value, List.of("GAK", "CLIENT", "OPS"), required);
            case "location" -> normalizeSingle(value, List.of("上海办公室", "深圳办公室", "居家", "客户现场", "出差在途"), required);
            default -> value;
        };
    }

    private List<String> workLogTypeValues() {
        return List.of("NORMAL", "OVERTIME", "BUSINESS_TRIP", "CITY_BUSINESS_TRIP", "OUT_OF_CITY_BUSINESS_TRIP", "LEAVE");
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
