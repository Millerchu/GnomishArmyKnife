package com.gak.workbench.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gak.user.mapper.user.UserMapper;
import com.gak.workbench.domain.UserPreference;
import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.exception.WorkbenchPreferenceConflictException;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import com.gak.workbench.mapper.UserPreferenceMapper;
import com.gak.workbench.vo.WorkbenchPreferenceVO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 工作台单文档偏好及手工乐观并发控制。 */
@Service
public class WorkbenchPreferenceService {
    private static final String PREFERENCE_KEY = "workbench";
    private final UserPreferenceMapper mapper;
    private final WorkbenchPayloadNormalizer normalizer;
    private final UserMapper userMapper;

    public WorkbenchPreferenceService(UserPreferenceMapper mapper, WorkbenchPayloadNormalizer normalizer,
            UserMapper userMapper) {
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.userMapper = userMapper;
    }

    public WorkbenchPreferenceVO get(Long userId) {
        ensureCurrentUserExists(userId);
        return toView(mapper.find(userId, PREFERENCE_KEY));
    }

    /** READ_COMMITTED 保证写竞争结束后下一次 SELECT 可见胜出文档。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkbenchPreferenceVO save(Long userId, WorkbenchPreferenceRequest request) {
        ensureCurrentUserExists(userId);
        validateEnvelope(request);
        UserPreference preference = buildPreference(userId, request);
        int affected;
        if (request.version() == 0) {
            preference.setId(IdWorker.getId());
            preference.setVersion(1L);
            affected = mapper.insertIfAbsent(preference);
        } else {
            affected = mapper.updateVersion(preference);
            preference.setVersion(request.version() + 1);
        }
        if (affected == 0) {
            // 行不存在也不自动重建，返回 version=0 的最新空文档，由客户端显式重试创建。
            throw new WorkbenchPreferenceConflictException(get(userId));
        }
        return toView(preference);
    }

    private void validateEnvelope(WorkbenchPreferenceRequest request) {
        if (request == null || request.version() == null || request.version() < 0
                || request.version() == Long.MAX_VALUE || request.schemaVersion() == null) {
            throw new WorkbenchPreferenceInvalidException("version 必须为非负整数且可递增，schemaVersion 必填");
        }
    }

    /** 内存 token 可能晚于用户删除失效，与其他业务模块保持相同的 401 提示。 */
    private void ensureCurrentUserExists(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前登录用户不存在");
        }
    }

    private UserPreference buildPreference(Long userId, WorkbenchPreferenceRequest request) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setPrefKey(PREFERENCE_KEY);
        preference.setVersion(request.version());
        preference.setSchemaVersion(WorkbenchPayloadNormalizer.CURRENT_SCHEMA_VERSION);
        preference.setPayload(normalizer.normalize(request.payload(), request.schemaVersion()));
        LocalDateTime now = LocalDateTime.now();
        preference.setCreatedAt(now);
        preference.setUpdatedAt(now);
        return preference;
    }

    private WorkbenchPreferenceVO toView(UserPreference preference) {
        if (preference == null) {
            return WorkbenchPreferenceVO.empty();
        }
        return new WorkbenchPreferenceVO(preference.getVersion(), preference.getSchemaVersion(),
                normalizer.normalizeStored(preference.getPayload(), preference.getSchemaVersion()), preference.getUpdatedAt());
    }
}
