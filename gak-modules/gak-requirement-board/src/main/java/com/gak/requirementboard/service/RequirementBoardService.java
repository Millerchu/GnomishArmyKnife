package com.gak.requirementboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.requirementboard.domain.Requirement;
import com.gak.requirementboard.domain.RequirementProgressLog;
import com.gak.requirementboard.dto.CreateRequirementRequest;
import com.gak.requirementboard.dto.RequirementQueryRequest;
import com.gak.requirementboard.dto.UpdateRequirementProgressRequest;
import com.gak.requirementboard.dto.UpdateRequirementRequest;
import com.gak.requirementboard.enums.RequirementPriority;
import com.gak.requirementboard.enums.RequirementStatus;
import com.gak.requirementboard.enums.RequirementType;
import com.gak.requirementboard.mapper.RequirementMapper;
import com.gak.requirementboard.mapper.RequirementAppMapper;
import com.gak.requirementboard.mapper.RequirementProgressLogMapper;
import com.gak.requirementboard.vo.RequirementAppOptionVO;
import com.gak.requirementboard.vo.RequirementDetailVO;
import com.gak.requirementboard.vo.RequirementListVO;
import com.gak.requirementboard.vo.RequirementPageVO;
import com.gak.requirementboard.vo.RequirementProgressLogVO;
import com.gak.requirementboard.vo.RequirementStatusCountVO;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 共享需求的查询、协作与进度流转服务。
 */
@Service
public class RequirementBoardService {

    private static final String REQUIREMENT_PENDING_REVIEW_REMARK = "提交需求";
    private static final String BUG_PENDING_REVIEW_REMARK = "提交Bug";
    private static final String REQUIREMENT_NOT_FOUND_MESSAGE = "反馈不存在或已删除";
    private static final String UNKNOWN_USER_NAME = "未知用户";
    private static final Map<String, String> SYSTEM_APPLICATIONS = buildSystemApplications();

    private final RequirementMapper requirementMapper;
    private final RequirementProgressLogMapper requirementProgressLogMapper;
    private final RequirementAppMapper requirementAppMapper;
    private final UserMapper userMapper;

    public RequirementBoardService(RequirementMapper requirementMapper,
                                   RequirementProgressLogMapper requirementProgressLogMapper,
                                   RequirementAppMapper requirementAppMapper,
                                   UserMapper userMapper) {
        this.requirementMapper = requirementMapper;
        this.requirementProgressLogMapper = requirementProgressLogMapper;
        this.requirementAppMapper = requirementAppMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询所有登录用户共享的需求看板，状态统计不随当前筛选条件缩小。
     */
    public RequirementPageVO page(Long currentUserId, RequirementQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        String normalizedStatus = normalizeOptionalStatus(request.getStatus());
        String normalizedAppCode = normalizeOptionalAppCode(request.getAppCode());
        String normalizedPriority = normalizeOptionalPriority(request.getPriority());
        String normalizedType = normalizeOptionalType(request.getType());
        String keyword = trimToNull(request.getKeyword());

        QueryWrapper<Requirement> wrapper = new QueryWrapper<>();
        if (normalizedStatus != null) {
            wrapper.eq("status", normalizedStatus);
        }
        if (normalizedAppCode != null) {
            wrapper.eq("app_code", normalizedAppCode);
        }
        if (normalizedPriority != null) {
            wrapper.eq("priority", normalizedPriority);
        }
        if (normalizedType != null) {
            wrapper.eq("type", normalizedType);
        }
        if (keyword != null) {
            wrapper.and(condition -> condition.like("title", keyword).or().like("description", keyword));
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");

        List<Requirement> filteredRequirements = requirementMapper.selectList(wrapper);
        long total = filteredRequirements.size();
        long fromIndex = Math.max((request.getPageNo() - 1L) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        List<Requirement> pageRequirements = fromIndex >= total
                ? List.of()
                : filteredRequirements.subList((int) fromIndex, (int) toIndex);
        Map<Long, String> creatorNameMap = loadUserNameMap(extractCreatorIds(pageRequirements));

        RequirementPageVO result = new RequirementPageVO();
        result.setList(toListVOs(pageRequirements, creatorNameMap));
        result.setTotal(total);
        result.setStatusCounts(buildStatusCounts());
        return result;
    }

    public List<RequirementAppOptionVO> listApps(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        Map<String, RequirementAppOptionVO> mergedApps = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : SYSTEM_APPLICATIONS.entrySet()) {
            mergedApps.put(entry.getKey(), buildAppOption(entry.getKey(), entry.getValue()));
        }
        for (RequirementAppOptionVO app : requirementAppMapper.selectEnabledApps()) {
            mergedApps.putIfAbsent(app.getAppCode(), app);
        }
        return new ArrayList<>(mergedApps.values());
    }

    public RequirementDetailVO getDetail(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        Requirement requirement = getRequirementOrThrow(id);

        QueryWrapper<RequirementProgressLog> wrapper = new QueryWrapper<>();
        wrapper.eq("requirement_id", id).orderByAsc("created_at").orderByAsc("id");
        List<RequirementProgressLog> progressLogs = requirementProgressLogMapper.selectList(wrapper);

        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(requirement.getCreatorUserId());
        for (RequirementProgressLog progressLog : progressLogs) {
            userIds.add(progressLog.getOperatorUserId());
        }
        Map<Long, String> userNameMap = loadUserNameMap(userIds);

        RequirementDetailVO detail = toDetailVO(requirement, userNameMap);
        detail.setProgressLogs(toProgressLogVOs(progressLogs, userNameMap));
        return detail;
    }

    @Transactional
    public RequirementDetailVO create(Long currentUserId, CreateRequirementRequest request) {
        ensureCurrentUserExists(currentUserId);
        RequirementAppOptionVO app = getEnabledAppOrThrow(request.getAppCode());
        LocalDateTime now = LocalDateTime.now();

        Requirement requirement = new Requirement();
        requirement.setCreatorUserId(currentUserId);
        requirement.setAppCode(app.getAppCode());
        requirement.setAppName(app.getAppName());
        requirement.setType(normalizeRequiredType(request.getType()));
        requirement.setTitle(trimRequired(request.getTitle(), "标题不能为空"));
        requirement.setDescription(trimToNull(request.getDescription()));
        requirement.setPriority(normalizeRequiredPriority(request.getPriority()));
        requirement.setStatus(RequirementStatus.PENDING_REVIEW.name());
        requirement.setVersion(1L);
        requirement.setCreatedAt(now);
        requirement.setUpdatedAt(now);
        requirementMapper.insert(requirement);

        RequirementProgressLog progressLog = new RequirementProgressLog();
        progressLog.setRequirementId(requirement.getId());
        progressLog.setStatus(requirement.getStatus());
        progressLog.setRemark(initialProgressRemark(requirement.getType()));
        progressLog.setOperatorUserId(currentUserId);
        progressLog.setCreatedAt(now);
        requirementProgressLogMapper.insert(progressLog);

        Map<Long, String> userNameMap = loadUserNameMap(List.of(currentUserId));
        RequirementDetailVO detail = toDetailVO(requirement, userNameMap);
        detail.setProgressLogs(toProgressLogVOs(List.of(progressLog), userNameMap));
        return detail;
    }

    @Transactional
    public RequirementDetailVO updateContent(Long currentUserId, Long id, UpdateRequirementRequest request) {
        ensureCurrentUserExists(currentUserId);
        Requirement requirement = getRequirementOrThrow(id);
        if (!currentUserId.equals(requirement.getCreatorUserId())) {
            throw new BusinessException("REQUIREMENT_EDIT_FORBIDDEN", "只能编辑自己提交的反馈");
        }

        RequirementAppOptionVO app = getEnabledAppOrThrow(request.getAppCode());
        LocalDateTime now = LocalDateTime.now();
        updateRequirementOrThrow(id, request.getVersion(), null, app, normalizeRequiredType(request.getType()),
                trimRequired(request.getTitle(), "标题不能为空"), trimToNull(request.getDescription()),
                normalizeRequiredPriority(request.getPriority()), now);
        return getDetail(currentUserId, id);
    }

    @Transactional
    public RequirementDetailVO updateProgress(Long currentUserId,
                                              Long id,
                                              UpdateRequirementProgressRequest request) {
        ensureCurrentUserExists(currentUserId);
        String normalizedStatus = normalizeRequiredStatus(request.getStatus());
        Requirement current = getRequirementOrThrow(id);
        if (normalizedStatus.equals(current.getStatus())) {
            throw new BusinessException("REQUIREMENT_STATUS_UNCHANGED", "请选择与当前状态不同的反馈状态");
        }

        LocalDateTime now = LocalDateTime.now();
        updateRequirementOrThrow(id, request.getVersion(), normalizedStatus, null, null, null, null, null, now);

        RequirementProgressLog progressLog = new RequirementProgressLog();
        progressLog.setRequirementId(id);
        progressLog.setStatus(normalizedStatus);
        progressLog.setRemark(trimToNull(request.getRemark()));
        progressLog.setOperatorUserId(currentUserId);
        progressLog.setCreatedAt(now);
        requirementProgressLogMapper.insert(progressLog);
        return getDetail(currentUserId, id);
    }

    @Transactional
    public void delete(Long currentUserId, Long id, Long version) {
        User currentUser = ensureCurrentUserExists(currentUserId);
        Requirement requirement = getRequirementOrThrow(id);
        if (!currentUserId.equals(requirement.getCreatorUserId()) && !isAdmin(currentUser)) {
            throw new BusinessException("REQUIREMENT_DELETE_FORBIDDEN", "只能删除自己提交的反馈");
        }

        QueryWrapper<Requirement> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("version", version);
        if (requirementMapper.delete(wrapper) == 0) {
            throw new BusinessException("REQUIREMENT_VERSION_CONFLICT", "反馈已被其他用户更新，请刷新后重试");
        }
    }

    /**
     * 利用版本号原子更新，防止多人协作时后保存的内容覆盖先保存的结果。
     */
    private void updateRequirementOrThrow(Long id,
                                          Long version,
                                          String status,
                                          RequirementAppOptionVO app,
                                          String type,
                                          String title,
                                          String description,
                                          String priority,
                                          LocalDateTime updatedAt) {
        Requirement updated = new Requirement();
        if (status != null) {
            updated.setStatus(status);
        }
        if (app != null) {
            updated.setAppCode(app.getAppCode());
            updated.setAppName(app.getAppName());
        }
        if (type != null) {
            updated.setType(type);
        }
        if (title != null) {
            updated.setTitle(title);
            updated.setDescription(description);
        }
        if (priority != null) {
            updated.setPriority(priority);
        }
        updated.setVersion(version + 1L);
        updated.setUpdatedAt(updatedAt);

        UpdateWrapper<Requirement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).eq("version", version);
        if (requirementMapper.update(updated, wrapper) == 0) {
            throw new BusinessException("REQUIREMENT_VERSION_CONFLICT", "反馈已被其他用户更新，请刷新后重试");
        }
    }

    private List<RequirementStatusCountVO> buildStatusCounts() {
        List<RequirementStatusCountVO> result = new ArrayList<>();
        for (RequirementStatus status : RequirementStatus.values()) {
            QueryWrapper<Requirement> wrapper = new QueryWrapper<>();
            wrapper.eq("status", status.name());

            RequirementStatusCountVO count = new RequirementStatusCountVO();
            count.setStatus(status.name());
            Long value = requirementMapper.selectCount(wrapper);
            count.setCount(value == null ? 0L : value);
            result.add(count);
        }
        return result;
    }

    private Requirement getRequirementOrThrow(Long id) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, REQUIREMENT_NOT_FOUND_MESSAGE);
        }
        return requirement;
    }

    private User ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前用户不存在");
        }
        return user;
    }

    private boolean isAdmin(User user) {
        return UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(user.getRoleCode());
    }

    private String normalizeOptionalStatus(String status) {
        String normalized = trimToNull(status);
        return normalized == null ? null : normalizeRequiredStatus(normalized);
    }

    private String normalizeOptionalAppCode(String appCode) {
        String normalized = trimToNull(appCode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalPriority(String priority) {
        String normalized = trimToNull(priority);
        return normalized == null ? null : normalizeRequiredPriority(normalized);
    }

    private String normalizeOptionalType(String type) {
        String normalized = trimToNull(type);
        return normalized == null ? null : normalizeRequiredType(normalized);
    }

    private String normalizeRequiredPriority(String priority) {
        String normalized = trimRequired(priority, "优先级不能为空").toUpperCase(Locale.ROOT);
        try {
            return RequirementPriority.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("REQUIREMENT_PRIORITY_INVALID", "优先级不合法");
        }
    }

    private RequirementAppOptionVO getEnabledAppOrThrow(String appCode) {
        String normalizedAppCode = normalizeOptionalAppCode(appCode);
        if (normalizedAppCode == null) {
            throw new BusinessException("REQUIREMENT_APP_REQUIRED", "请选择反馈所属应用");
        }
        String systemAppName = SYSTEM_APPLICATIONS.get(normalizedAppCode);
        if (systemAppName != null) {
            return buildAppOption(normalizedAppCode, systemAppName);
        }
        RequirementAppOptionVO app = requirementAppMapper.selectEnabledAppByCode(normalizedAppCode);
        if (app == null) {
            throw new BusinessException("REQUIREMENT_APP_INVALID", "所选应用不存在或已停用");
        }
        return app;
    }

    private String normalizeRequiredType(String type) {
        String normalized = trimRequired(type, "类型不能为空").toUpperCase(Locale.ROOT);
        try {
            return RequirementType.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("REQUIREMENT_TYPE_INVALID", "类型不合法");
        }
    }

    private String initialProgressRemark(String type) {
        return RequirementType.BUG.name().equals(type)
                ? BUG_PENDING_REVIEW_REMARK
                : REQUIREMENT_PENDING_REVIEW_REMARK;
    }

    private String normalizeRequiredStatus(String status) {
        String normalized = trimRequired(status, "反馈状态不能为空").toUpperCase(Locale.ROOT);
        try {
            return RequirementStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("REQUIREMENT_STATUS_INVALID", "反馈状态不合法");
        }
    }

    private List<RequirementListVO> toListVOs(List<Requirement> requirements, Map<Long, String> userNameMap) {
        List<RequirementListVO> result = new ArrayList<>();
        for (Requirement requirement : requirements) {
            result.add(toListVO(requirement, userNameMap));
        }
        return result;
    }

    private RequirementListVO toListVO(Requirement requirement, Map<Long, String> userNameMap) {
        RequirementListVO vo = new RequirementListVO();
        copyRequirement(requirement, vo, userNameMap);
        return vo;
    }

    private RequirementDetailVO toDetailVO(Requirement requirement, Map<Long, String> userNameMap) {
        RequirementDetailVO vo = new RequirementDetailVO();
        copyRequirement(requirement, vo, userNameMap);
        return vo;
    }

    private void copyRequirement(Requirement source, RequirementListVO target, Map<Long, String> userNameMap) {
        target.setId(source.getId());
        target.setCreatorUserId(source.getCreatorUserId());
        target.setCreatorName(userNameMap.getOrDefault(source.getCreatorUserId(), UNKNOWN_USER_NAME));
        target.setAppCode(source.getAppCode());
        target.setAppName(source.getAppName());
        target.setType(source.getType());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setPriority(source.getPriority());
        target.setStatus(source.getStatus());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private List<RequirementProgressLogVO> toProgressLogVOs(List<RequirementProgressLog> progressLogs,
                                                             Map<Long, String> userNameMap) {
        List<RequirementProgressLogVO> result = new ArrayList<>();
        for (RequirementProgressLog progressLog : progressLogs) {
            RequirementProgressLogVO vo = new RequirementProgressLogVO();
            vo.setId(progressLog.getId());
            vo.setStatus(progressLog.getStatus());
            vo.setRemark(progressLog.getRemark());
            vo.setOperatorUserId(progressLog.getOperatorUserId());
            vo.setOperatorName(userNameMap.getOrDefault(progressLog.getOperatorUserId(), UNKNOWN_USER_NAME));
            vo.setCreatedAt(progressLog.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    private Collection<Long> extractCreatorIds(List<Requirement> requirements) {
        Set<Long> creatorIds = new LinkedHashSet<>();
        for (Requirement requirement : requirements) {
            creatorIds.add(requirement.getCreatorUserId());
        }
        return creatorIds;
    }

    private Map<Long, String> loadUserNameMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> result = new HashMap<>();
        for (User user : users) {
            String displayName = trimToNull(user.getDisplayName());
            result.put(user.getId(), displayName == null ? user.getUsername() : displayName);
        }
        return result;
    }

    private String trimRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("REQUIREMENT_CONTENT_INVALID", message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private RequirementAppOptionVO buildAppOption(String appCode, String appName) {
        RequirementAppOptionVO app = new RequirementAppOptionVO();
        app.setAppCode(appCode);
        app.setAppName(appName);
        return app;
    }

    /**
     * 系统菜单不进入应用管理目录，但用户仍需要把反馈精确关联到这些功能。
     */
    private static Map<String, String> buildSystemApplications() {
        Map<String, String> systemApps = new LinkedHashMap<>();
        systemApps.put("APP_USER_MANAGEMENT", "用户管理");
        systemApps.put("APP_APP_MANAGEMENT", "应用管理");
        systemApps.put("APP_PERMISSION_MANAGEMENT", "权限管理");
        systemApps.put("APP_DATA_MIGRATION", "数据迁移");
        systemApps.put("APP_DATA_DICTIONARY", "数据字典");
        systemApps.put("APP_USER_BOARD", "用户看板");
        systemApps.put("APP_NEW_APPLICATION", "新应用");
        return Collections.unmodifiableMap(systemApps);
    }
}
