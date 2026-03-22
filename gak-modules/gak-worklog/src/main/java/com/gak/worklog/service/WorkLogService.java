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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int BRIEF_MAX_LENGTH = 80;

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
     * @param request 新增请求
     * @return 日志详情
     */
    @Transactional
    public WorkLogResponse create(CreateWorkLogRequest request) {
        List<String> typeCodes = normalizeAndValidateTypeCodes(request.getTypeCodes());
        validateDuplicateLogDate(request.getUserId(), request.getLogDate(), null);

        LocalDateTime now = LocalDateTime.now();
        WorkLog workLog = new WorkLog();
        workLog.setUserId(request.getUserId());
        workLog.setLogDate(request.getLogDate());
        workLog.setLocation(request.getLocation());
        workLog.setProjectCode(request.getProjectCode());
        workLog.setContent(request.getWorkItem());
        workLog.setZentaoNo(request.getZentaoNo());
        workLog.setPersonDay(request.getPersonDay());
        workLog.setOvertimeHours(request.getOvertimeHours());
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
     * @param id 主键 ID
     */
    @Transactional
    public void delete(Long id) {
        ensureExists(id);

        QueryWrapper<WorkLogType> typeWrapper = new QueryWrapper<>();
        typeWrapper.eq("work_log_id", id);
        workLogTypeMapper.delete(typeWrapper);
        workLogMapper.deleteById(id);
    }

    /**
     * 更新日志。
     *
     * @param id 主键 ID
     * @param request 更新请求
     * @return 更新后详情
     */
    @Transactional
    public WorkLogResponse update(Long id, UpdateWorkLogRequest request) {
        WorkLog current = getByIdOrThrow(id);
        List<String> typeCodes = normalizeAndValidateTypeCodes(request.getTypeCodes());
        validateDuplicateLogDate(current.getUserId(), request.getLogDate(), id);

        current.setLogDate(request.getLogDate());
        current.setLocation(request.getLocation());
        current.setProjectCode(request.getProjectCode());
        current.setContent(request.getWorkItem());
        current.setZentaoNo(request.getZentaoNo());
        current.setPersonDay(request.getPersonDay());
        current.setOvertimeHours(request.getOvertimeHours());
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
     * @param id 主键 ID
     * @return 日志详情
     */
    public WorkLogResponse get(Long id) {
        WorkLog workLog = getByIdOrThrow(id);
        return buildResponse(workLog, getTypeCodesByWorkLogId(id));
    }

    /**
     * 条件列表查询。
     *
     * @param userId 用户 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param typeCode 类型编码（可选）
     * @return 日志列表
     */
    public List<WorkLogResponse> list(Long userId, LocalDate startDate, LocalDate endDate, String typeCode) {
        if (userId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "userId 不能为空");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(BAD_REQUEST, "startDate 不能大于 endDate");
        }
        String normalizedTypeCode = normalizeOptionalTypeCode(typeCode);

        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.ge(startDate != null, "log_date", startDate);
        wrapper.le(endDate != null, "log_date", endDate);
        wrapper.orderByDesc("log_date").orderByDesc("updated_at");
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
     * @param userId 用户 ID
     * @param refDate 参考日期（取该日期所在周），默认今天
     * @return 当周简述列表
     */
    public List<WeeklyWorkLogBriefResponse> listWeeklyBrief(Long userId, LocalDate refDate) {
        if (userId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "userId 不能为空");
        }

        LocalDate referenceDate = refDate == null ? LocalDate.now() : refDate;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .ge("log_date", weekStart)
                .le("log_date", weekEnd)
                .orderByAsc("log_date")
                .orderByDesc("updated_at");

        List<WorkLog> workLogs = workLogMapper.selectList(wrapper);
        Map<Long, List<String>> typeMap = loadTypeMap(workLogs);
        List<WeeklyWorkLogBriefResponse> result = new ArrayList<>();

        for (WorkLog workLog : workLogs) {
            WeeklyWorkLogBriefResponse response = new WeeklyWorkLogBriefResponse();
            response.setId(workLog.getId());
            response.setLogDate(workLog.getLogDate());
            response.setTypeCodes(typeMap.getOrDefault(workLog.getId(), List.of()));
            response.setProjectCode(workLog.getProjectCode());
            response.setBrief(extractBrief(workLog.getContent()));
            response.setPersonDay(workLog.getPersonDay());
            response.setOvertimeHours(workLog.getOvertimeHours());
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

    private void ensureExists(Long id) {
        getByIdOrThrow(id);
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
                    "typeCodes",
                    typeCodes,
                    true
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "typeCode 非法");
        }
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

    private void validateDuplicateLogDate(Long userId, LocalDate logDate, Long ignoreLogId) {
        QueryWrapper<WorkLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("log_date", logDate);

        if (ignoreLogId != null) {
            wrapper.ne("id", ignoreLogId);
        }

        Long count = workLogMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new ResponseStatusException(BAD_REQUEST, "该用户在当前日期已存在工作日志");
        }
    }

    private List<String> getTypeCodesByWorkLogId(Long workLogId) {
        QueryWrapper<WorkLogType> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("work_log_id", workLogId);
        queryWrapper.orderByAsc("created_at");

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

        List<WorkLogType> types = workLogTypeMapper.selectList(wrapper);
        types.sort(Comparator.comparing(WorkLogType::getCreatedAt));

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
                    "typeCodes",
                    typeCode,
                    false
            );
        } catch (BusinessException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "typeCode 非法");
        }
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
        response.setRemark(workLog.getRemark());
        response.setCreatedAt(workLog.getCreatedAt());
        response.setUpdatedAt(workLog.getUpdatedAt());
        return response;
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
