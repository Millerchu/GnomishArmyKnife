package com.gak.worklog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 工作日志服务。
 */
@Service
public class WorkLogService {

    private static final String APP_CODE = "APP_WORK_LOG";
    private static final String MODULE_CODE = "WORK_LOG";
    private static final String TYPE_CODES_FIELD = "typeCodes";
    private static final String PROJECT_CODE_FIELD = "projectCode";
    private static final String LOCATION_FIELD = "location";
    private static final String TYPE_CODE_CITY_BUSINESS_TRIP = "CITY_BUSINESS_TRIP";
    private static final String TYPE_CODE_OUT_OF_CITY_BUSINESS_TRIP = "OUT_OF_CITY_BUSINESS_TRIP";
    private static final String TYPE_CODE_LEGACY_BUSINESS_TRIP = "BUSINESS_TRIP";
    private static final String ALLOWANCE_SCENE_CITY = "CITY";
    private static final String ALLOWANCE_SCENE_OUT_OF_CITY_TRANSIT = "OUT_OF_CITY_TRANSIT";
    private static final String ALLOWANCE_SCENE_OUT_OF_CITY_DAILY = "OUT_OF_CITY_DAILY";
    private static final int BRIEF_MAX_LENGTH = 80;
    private static final BigDecimal MAX_DAILY_PERSON_DAY = BigDecimal.ONE.setScale(1, RoundingMode.HALF_UP);
    private static final LocalTime STANDARD_OFF_WORK_TIME = LocalTime.of(18, 0);
    private static final BigDecimal ZERO_HOURS = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CITY_BUSINESS_TRIP_ALLOWANCE = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal OUT_OF_CITY_TRANSIT_ALLOWANCE = BigDecimal.valueOf(110).setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal OUT_OF_CITY_DAILY_ALLOWANCE = BigDecimal.valueOf(160).setScale(2, RoundingMode.HALF_UP);

    private final WorkLogMapper workLogMapper;
    private final WorkLogTypeMapper workLogTypeMapper;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;

    public WorkLogService(WorkLogMapper workLogMapper,
                          WorkLogTypeMapper workLogTypeMapper,
                          DataDictionaryUsageSupport dataDictionaryUsageSupport) {
        this.workLogMapper = workLogMapper;
        this.workLogTypeMapper = workLogTypeMapper;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
    }

    /**
     * 新增日志。
     *
     * @param currentUserId 当前登录用户 ID
     * @param request 新增请求
     * @return 日志详情
     */
    @Transactional
    public WorkLogResponse create(Long currentUserId, CreateWorkLogRequest request) {
        List<String> typeCodes = normalizeAndValidateTypeCodes(request.getTypeCodes());
        String projectCode = normalizeRequiredProjectCode(request.getProjectCode());
        String location = normalizeOptionalLocation(request.getLocation());
        workLogMapper.lockUserWorkLogs(currentUserId);
        validateDuplicateProject(currentUserId, request.getLogDate(), projectCode, null);
        validateDailyPersonDay(currentUserId, request.getLogDate(), request.getPersonDay(), null);
        BigDecimal overtimeHours = resolveOvertimeHours(
                request.getLogDate(),
                request.getOffWorkTime(),
                request.getOvertimeHours()
        );
        BusinessTripAllowance businessTripAllowance = resolveBusinessTripAllowance(
                typeCodes,
                request.getBusinessTripAllowanceScene(),
                request.getBusinessTripReimbursed()
        );

        LocalDateTime now = LocalDateTime.now();
        WorkLog workLog = new WorkLog();
        workLog.setUserId(currentUserId);
        workLog.setLogDate(request.getLogDate());
        workLog.setLocation(location);
        workLog.setProjectCode(projectCode);
        workLog.setContent(request.getWorkItem());
        workLog.setZentaoNo(request.getZentaoNo());
        workLog.setPersonDay(request.getPersonDay());
        workLog.setOvertimeHours(overtimeHours);
        workLog.setOffWorkTime(request.getOffWorkTime());
        workLog.setBusinessTripAllowanceScene(businessTripAllowance.scene());
        workLog.setBusinessTripAllowanceAmount(businessTripAllowance.amount());
        workLog.setBusinessTripReimbursed(businessTripAllowance.reimbursed());
        workLog.setRemark(request.getRemark());
        workLog.setCreatedAt(now);
        workLog.setUpdatedAt(now);
        workLogMapper.insert(workLog);

        saveTypeRelations(workLog.getId(), typeCodes, now);
        return buildResponse(workLog, typeCodes);
    }

    /**
     * 删除日志。
     *
     * @param currentUserId 当前登录用户 ID
     * @param id 主键 ID
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        WorkLog current = getOwnedByIdOrThrow(currentUserId, id);

        QueryWrapper<WorkLogType> typeWrapper = new QueryWrapper<>();
        typeWrapper.eq("work_log_id", current.getId());
        workLogTypeMapper.delete(typeWrapper);
        workLogMapper.deleteById(current.getId());
    }

    /**
     * 更新日志。
     *
     * @param currentUserId 当前登录用户 ID
     * @param id 主键 ID
     * @param request 更新请求
     * @return 更新后详情
     */
    @Transactional
    public WorkLogResponse update(Long currentUserId, Long id, UpdateWorkLogRequest request) {
        WorkLog current = getOwnedByIdOrThrow(currentUserId, id);
        List<String> typeCodes = normalizeAndValidateTypeCodes(request.getTypeCodes());
        String projectCode = normalizeRequiredProjectCode(request.getProjectCode());
        String location = normalizeOptionalLocation(request.getLocation());
        workLogMapper.lockUserWorkLogs(currentUserId);
        validateDuplicateProject(currentUserId, request.getLogDate(), projectCode, id);
        validateDailyPersonDay(currentUserId, request.getLogDate(), request.getPersonDay(), id);
        BigDecimal overtimeHours = resolveOvertimeHours(
                request.getLogDate(),
                request.getOffWorkTime(),
                request.getOvertimeHours()
        );
        BusinessTripAllowance businessTripAllowance = resolveBusinessTripAllowance(
                typeCodes,
                request.getBusinessTripAllowanceScene(),
                request.getBusinessTripReimbursed()
        );

        current.setLogDate(request.getLogDate());
        current.setLocation(location);
        current.setProjectCode(projectCode);
        current.setContent(request.getWorkItem());
        current.setZentaoNo(request.getZentaoNo());
        current.setPersonDay(request.getPersonDay());
        current.setOvertimeHours(overtimeHours);
        current.setOffWorkTime(request.getOffWorkTime());
        current.setBusinessTripAllowanceScene(businessTripAllowance.scene());
        current.setBusinessTripAllowanceAmount(businessTripAllowance.amount());
        current.setBusinessTripReimbursed(businessTripAllowance.reimbursed());
        current.setRemark(request.getRemark());
        current.setUpdatedAt(LocalDateTime.now());
        workLogMapper.updateById(current);

        QueryWrapper<WorkLogType> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("work_log_id", id);
        workLogTypeMapper.delete(deleteWrapper);
        saveTypeRelations(id, typeCodes, LocalDateTime.now());

        return buildResponse(current, typeCodes);
    }

    /**
     * 查询日志详情。
     *
     * @param currentUserId 当前登录用户 ID
     * @param id 主键 ID
     * @return 日志详情
     */
    public WorkLogResponse get(Long currentUserId, Long id) {
        WorkLog workLog = getOwnedByIdOrThrow(currentUserId, id);
        return buildResponse(workLog, getTypeCodesByWorkLogId(id));
    }

    /**
     * 条件列表查询。
     *
     * @param currentUserId 当前登录用户 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param typeCode 类型编码（可选）
     * @return 日志列表
     */
    public List<WorkLogResponse> list(Long currentUserId, LocalDate startDate, LocalDate endDate, String typeCode) {
        if (currentUserId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "当前用户不能为空");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(BAD_REQUEST, "startDate 不能大于 endDate");
        }
        String normalizedTypeCode = normalizeOptionalTypeCode(typeCode);

        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId);
        wrapper.ge(startDate != null, "log_date", startDate);
        wrapper.le(endDate != null, "log_date", endDate);
        wrapper.orderByDesc("log_date").orderByDesc("updated_at").orderByDesc("id");
        List<WorkLog> workLogs = workLogMapper.selectList(wrapper);

        Map<Long, List<String>> typeMap = loadTypeMap(workLogs);
        List<WorkLogResponse> result = new ArrayList<>();
        for (WorkLog workLog : workLogs) {
            List<String> types = typeMap.getOrDefault(workLog.getId(), List.of());
            if (normalizedTypeCode != null && !types.contains(normalizedTypeCode)) {
                continue;
            }
            result.add(buildResponse(workLog, types));
        }
        return result;
    }

    /**
     * 查询当周日志简述（用于主界面，周一为一周第一天）。
     *
     * @param currentUserId 当前登录用户 ID
     * @param refDate 参考日期（取该日期所在周），默认今天
     * @return 当周简述列表
     */
    public List<WeeklyWorkLogBriefResponse> listWeeklyBrief(Long currentUserId, LocalDate refDate) {
        if (currentUserId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "当前用户不能为空");
        }

        LocalDate referenceDate = refDate == null ? LocalDate.now() : refDate;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId)
                .ge("log_date", weekStart)
                .le("log_date", weekEnd)
                .orderByAsc("log_date")
                .orderByDesc("updated_at")
                .orderByDesc("id");

        List<WorkLog> workLogs = workLogMapper.selectList(wrapper);
        Map<Long, List<String>> typeMap = loadTypeMap(workLogs);
        List<WeeklyWorkLogBriefResponse> result = new ArrayList<>();

        for (WorkLog workLog : workLogs) {
            WeeklyWorkLogBriefResponse response = new WeeklyWorkLogBriefResponse();
            response.setId(workLog.getId());
            response.setLogDate(workLog.getLogDate());
            response.setTypeCodes(typeMap.getOrDefault(workLog.getId(), List.of()));
            response.setLocation(workLog.getLocation());
            response.setProjectCode(workLog.getProjectCode());
            response.setBrief(extractBrief(workLog.getContent()));
            response.setPersonDay(workLog.getPersonDay());
            response.setOvertimeHours(workLog.getOvertimeHours());
            response.setOffWorkTime(workLog.getOffWorkTime());
            response.setBusinessTripAllowanceScene(workLog.getBusinessTripAllowanceScene());
            response.setBusinessTripAllowanceAmount(normalizeAllowanceAmount(workLog.getBusinessTripAllowanceAmount()));
            response.setBusinessTripReimbursed(Boolean.TRUE.equals(workLog.getBusinessTripReimbursed()));
            response.setRemark(workLog.getRemark());
            result.add(response);
        }

        return result;
    }

    private WorkLog getByIdOrThrow(Long id) {
        WorkLog workLog = workLogMapper.selectById(id);
        if (workLog == null) {
            throw new ResponseStatusException(NOT_FOUND, "工作日志不存在");
        }
        return workLog;
    }

    private WorkLog getOwnedByIdOrThrow(Long currentUserId, Long id) {
        WorkLog workLog = getByIdOrThrow(id);
        if (!Objects.equals(workLog.getUserId(), currentUserId)) {
            throw new ResponseStatusException(NOT_FOUND, "工作日志不存在");
        }
        return workLog;
    }

    private List<String> normalizeAndValidateTypeCodes(List<String> typeCodes) {
        if (typeCodes == null || typeCodes.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "typeCodes 不能为空");
        }
        try {
            // 工作日志类型已经改成字典驱动，仍然保留多选去重的写入语义。
            return dataDictionaryUsageSupport.normalizeMultiValueByUsage(
                    APP_CODE,
                    MODULE_CODE,
                    TYPE_CODES_FIELD,
                    typeCodes,
                    true
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "typeCode 非法");
        }
    }

    private String normalizeRequiredProjectCode(String projectCode) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(
                    APP_CODE,
                    MODULE_CODE,
                    PROJECT_CODE_FIELD,
                    projectCode,
                    true
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "projectCode 非法");
        }
    }

    private String normalizeOptionalLocation(String location) {
        return normalizeOptionalUsageValue(LOCATION_FIELD, location, "location 非法");
    }

    private void saveTypeRelations(Long workLogId, List<String> typeCodes, LocalDateTime now) {
        for (String typeCode : typeCodes) {
            WorkLogType type = new WorkLogType();
            type.setWorkLogId(workLogId);
            type.setTypeCode(typeCode);
            type.setCreatedAt(now);
            workLogTypeMapper.insert(type);
        }
    }

    private void validateDuplicateProject(Long userId,
                                          LocalDate logDate,
                                          String projectCode,
                                          Long ignoreLogId) {
        Long count = workLogMapper.countByUserDateAndProject(userId, logDate, projectCode, ignoreLogId);
        if (count != null && count > 0L) {
            throw new ResponseStatusException(BAD_REQUEST, "该用户在当前日期和项目下已存在工作日志");
        }
    }

    private void validateDailyPersonDay(Long userId,
                                        LocalDate logDate,
                                        BigDecimal requestedPersonDay,
                                        Long ignoreLogId) {
        BigDecimal existingPersonDay = workLogMapper.sumPersonDayByUserAndDate(userId, logDate, ignoreLogId);
        BigDecimal normalizedExisting = existingPersonDay == null ? BigDecimal.ZERO : existingPersonDay;
        BigDecimal normalizedRequested = requestedPersonDay == null ? BigDecimal.ZERO : requestedPersonDay;
        BigDecimal remainingPersonDay = MAX_DAILY_PERSON_DAY.subtract(normalizedExisting).max(BigDecimal.ZERO);
        if (normalizedExisting.add(normalizedRequested).compareTo(MAX_DAILY_PERSON_DAY) > 0) {
            String remainingText = remainingPersonDay.stripTrailingZeros().toPlainString();
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    logDate + " 当天总人天不能超过 1，当前剩余 " + remainingText + " 人天"
            );
        }
    }

    private List<String> getTypeCodesByWorkLogId(Long workLogId) {
        QueryWrapper<WorkLogType> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("work_log_id", workLogId);
        queryWrapper.orderByAsc("created_at").orderByAsc("id");

        List<WorkLogType> list = workLogTypeMapper.selectList(queryWrapper);
        List<String> result = new ArrayList<>();
        for (WorkLogType item : list) {
            result.add(item.getTypeCode());
        }
        return result;
    }

    private Map<Long, List<String>> loadTypeMap(List<WorkLog> workLogs) {
        Map<Long, List<String>> result = new HashMap<>();
        if (workLogs.isEmpty()) {
            return result;
        }

        List<Long> logIds = new ArrayList<>();
        for (WorkLog workLog : workLogs) {
            logIds.add(workLog.getId());
        }

        QueryWrapper<WorkLogType> wrapper = new QueryWrapper<>();
        wrapper.in("work_log_id", logIds);
        wrapper.orderByAsc("work_log_id").orderByAsc("created_at").orderByAsc("id");

        List<WorkLogType> types = workLogTypeMapper.selectList(wrapper);

        for (WorkLogType type : types) {
            result.computeIfAbsent(type.getWorkLogId(), key -> new ArrayList<>()).add(type.getTypeCode());
        }

        return result;
    }

    private String normalizeOptionalTypeCode(String typeCode) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(
                    APP_CODE,
                    MODULE_CODE,
                    TYPE_CODES_FIELD,
                    typeCode,
                    false
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "typeCode 非法");
        }
    }

    private String normalizeOptionalUsageValue(String bizFieldCode, String value, String message) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(
                    APP_CODE,
                    MODULE_CODE,
                    bizFieldCode,
                    value,
                    false
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }

    private BigDecimal resolveOvertimeHours(LocalDate logDate, LocalTime offWorkTime, BigDecimal requestOvertimeHours) {
        if (isWeekend(logDate)) {
            return normalizeOvertimeHours(requestOvertimeHours);
        }
        if (offWorkTime != null) {
            return calculateWorkdayOvertimeHours(offWorkTime);
        }
        // 兼容旧前端仍只提交 overtimeHours 的阶段，等前端切完 offWorkTime 后再收紧。
        return normalizeOvertimeHours(requestOvertimeHours);
    }

    private BigDecimal calculateWorkdayOvertimeHours(LocalTime offWorkTime) {
        if (offWorkTime == null || !offWorkTime.isAfter(STANDARD_OFF_WORK_TIME)) {
            return ZERO_HOURS;
        }
        long overtimeMinutes = java.time.Duration.between(STANDARD_OFF_WORK_TIME, offWorkTime).toMinutes();
        return BigDecimal.valueOf(overtimeMinutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeOvertimeHours(BigDecimal overtimeHours) {
        if (overtimeHours == null) {
            return ZERO_HOURS;
        }
        return overtimeHours.setScale(1, RoundingMode.HALF_UP);
    }

    private BusinessTripAllowance resolveBusinessTripAllowance(List<String> typeCodes,
                                                               String requestedScene,
                                                               Boolean requestedReimbursed) {
        boolean cityTrip = typeCodes.contains(TYPE_CODE_CITY_BUSINESS_TRIP);
        boolean outOfCityTrip = typeCodes.contains(TYPE_CODE_OUT_OF_CITY_BUSINESS_TRIP);
        boolean legacyTrip = typeCodes.contains(TYPE_CODE_LEGACY_BUSINESS_TRIP);

        if (cityTrip && outOfCityTrip) {
            throw new ResponseStatusException(BAD_REQUEST, "市内出差和市外出差不能同时选择");
        }
        if (cityTrip) {
            return new BusinessTripAllowance(ALLOWANCE_SCENE_CITY, CITY_BUSINESS_TRIP_ALLOWANCE, Boolean.TRUE.equals(requestedReimbursed));
        }
        if (outOfCityTrip || legacyTrip) {
            String scene = normalizeOutOfCityAllowanceScene(requestedScene);
            BigDecimal amount = ALLOWANCE_SCENE_OUT_OF_CITY_TRANSIT.equals(scene)
                    ? OUT_OF_CITY_TRANSIT_ALLOWANCE
                    : OUT_OF_CITY_DAILY_ALLOWANCE;
            return new BusinessTripAllowance(scene, amount, Boolean.TRUE.equals(requestedReimbursed));
        }
        return new BusinessTripAllowance(null, ZERO_AMOUNT, false);
    }

    private String normalizeOutOfCityAllowanceScene(String requestedScene) {
        if (requestedScene == null || requestedScene.isBlank()) {
            return ALLOWANCE_SCENE_OUT_OF_CITY_DAILY;
        }
        String scene = requestedScene.trim();
        if (ALLOWANCE_SCENE_OUT_OF_CITY_TRANSIT.equals(scene) || ALLOWANCE_SCENE_OUT_OF_CITY_DAILY.equals(scene)) {
            return scene;
        }
        throw new ResponseStatusException(BAD_REQUEST, "市外出差补助场景非法");
    }

    private BigDecimal normalizeAllowanceAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO_AMOUNT;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isWeekend(LocalDate logDate) {
        DayOfWeek dayOfWeek = logDate.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private WorkLogResponse buildResponse(WorkLog workLog, List<String> typeCodes) {
        WorkLogResponse response = new WorkLogResponse();
        response.setId(workLog.getId());
        response.setUserId(workLog.getUserId());
        response.setLogDate(workLog.getLogDate());
        response.setTypeCodes(typeCodes);
        response.setLocation(workLog.getLocation());
        response.setProjectCode(workLog.getProjectCode());
        response.setWorkItem(workLog.getContent());
        response.setZentaoNo(workLog.getZentaoNo());
        response.setPersonDay(workLog.getPersonDay());
        response.setOvertimeHours(workLog.getOvertimeHours());
        response.setOffWorkTime(workLog.getOffWorkTime());
        response.setBusinessTripAllowanceScene(workLog.getBusinessTripAllowanceScene());
        response.setBusinessTripAllowanceAmount(normalizeAllowanceAmount(workLog.getBusinessTripAllowanceAmount()));
        response.setBusinessTripReimbursed(Boolean.TRUE.equals(workLog.getBusinessTripReimbursed()));
        response.setRemark(workLog.getRemark());
        response.setCreatedAt(workLog.getCreatedAt());
        response.setUpdatedAt(workLog.getUpdatedAt());
        return response;
    }

    private record BusinessTripAllowance(String scene, BigDecimal amount, Boolean reimbursed) {
    }

    private String extractBrief(String workItem) {
        if (workItem == null || workItem.isBlank()) {
            return "";
        }

        String[] lines = workItem.split("\\n");
        String firstLine = lines[0].trim();
        if (firstLine.length() <= BRIEF_MAX_LENGTH) {
            return firstLine;
        }
        return firstLine.substring(0, BRIEF_MAX_LENGTH) + "...";
    }
}
