package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.passwordmemo.domain.PasswordMemo;
import com.gak.passwordmemo.domain.PasswordMemoHistory;
import com.gak.passwordmemo.mapper.PasswordMemoHistoryMapper;
import com.gak.passwordmemo.mapper.PasswordMemoMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 密码备忘录迁移处理器。
 */
@Service
public class PasswordMemoMigrationHandler implements MigrationResourceHandler {

    private static final String APP_CODE = "APP_PASSWORD_MEMO";

    private final PasswordMemoMapper passwordMemoMapper;
    private final PasswordMemoHistoryMapper passwordMemoHistoryMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public PasswordMemoMigrationHandler(PasswordMemoMapper passwordMemoMapper,
                                        PasswordMemoHistoryMapper passwordMemoHistoryMapper,
                                        UserMapper userMapper,
                                        DataMigrationArchiveService archiveService) {
        this.passwordMemoMapper = passwordMemoMapper;
        this.passwordMemoHistoryMapper = passwordMemoHistoryMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return APP_CODE;
    }

    @Override
    public String resourceName() {
        return "密码备忘录";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "business/" + APP_CODE + "/data.json";
    }

    @Override
    public int order() {
        return 120;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<PasswordMemo> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("owner_user_id").orderByAsc("created_at").orderByAsc("id");
        List<PasswordMemo> memos = passwordMemoMapper.selectList(wrapper);
        QueryWrapper<PasswordMemoHistory> historyWrapper = new QueryWrapper<>();
        historyWrapper.orderByAsc("owner_user_id").orderByAsc("memo_id").orderByAsc("usage_started_at").orderByAsc("id");
        List<PasswordMemoHistory> histories = passwordMemoHistoryMapper.selectList(historyWrapper);
        return new MigrationResourceExportData(
                resourceCode(), entryPath(), new Payload(memos, histories), memos.size(), 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        Map<Long, Long> targetMemoIds = new HashMap<>();
        for (PasswordMemo source : payload.getMemos()) {
            if (source == null || !StringUtils.hasText(source.getSiteName())) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            if (targetUserId == null) {
                throw new BusinessException("DATA_MIGRATION_PASSWORD_MEMO_USER_MISSING",
                        "密码备忘录依赖的用户不存在: " + source.getOwnerUserId());
            }
            PasswordMemo existing = findExisting(targetUserId, source.getSiteName(), source.getSiteUrl(), source.getUsername());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PASSWORD_MEMO_CONFLICT", "密码备忘录已存在: " + source.getSiteName());
                }
                source.setOwnerUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                passwordMemoMapper.updateById(existing);
                targetMemoIds.put(source.getId(), existing.getId());
                importedCount++;
                continue;
            }
            PasswordMemo insertMemo = copyMemo(source);
            insertMemo.setOwnerUserId(targetUserId);
            if (insertMemo.getId() != null && passwordMemoMapper.selectById(insertMemo.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PASSWORD_MEMO_ID_CONFLICT", "密码备忘录 ID 冲突: " + insertMemo.getId());
                }
                insertMemo.setId(null);
            }
            passwordMemoMapper.insert(insertMemo);
            targetMemoIds.put(source.getId(), insertMemo.getId());
            importedCount++;
        }
        importPasswordHistories(payload.getHistories(), targetMemoIds, context);
        return MigrationResourceImportResult.success(importedCount, 0L, "密码备忘录导入完成");
    }

    private void importPasswordHistories(List<PasswordMemoHistory> histories,
                                         Map<Long, Long> targetMemoIds,
                                         ImportContext context) {
        if (histories == null || histories.isEmpty()) {
            return;
        }
        for (PasswordMemoHistory source : histories) {
            Long targetMemoId = targetMemoIds.get(source.getMemoId());
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            if (targetMemoId == null || targetUserId == null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PASSWORD_HISTORY_DEPENDENCY_MISSING",
                            "历史密码依赖的备忘录或用户不存在: " + source.getMemoId());
                }
                continue;
            }
            if (findExistingHistory(targetMemoId, source) != null) {
                continue;
            }
            PasswordMemoHistory insertHistory = new PasswordMemoHistory();
            DataMigrationBeanMergeSupport.overwrite(source, insertHistory);
            insertHistory.setMemoId(targetMemoId);
            insertHistory.setOwnerUserId(targetUserId);
            if (insertHistory.getId() != null && passwordMemoHistoryMapper.selectById(insertHistory.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PASSWORD_HISTORY_ID_CONFLICT",
                            "历史密码 ID 冲突: " + insertHistory.getId());
                }
                insertHistory.setId(null);
            }
            passwordMemoHistoryMapper.insert(insertHistory);
        }
    }

    private PasswordMemoHistory findExistingHistory(Long memoId, PasswordMemoHistory source) {
        QueryWrapper<PasswordMemoHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("memo_id", memoId)
                .eq("usage_started_at", source.getUsageStartedAt())
                .eq("usage_ended_at", source.getUsageEndedAt());
        return passwordMemoHistoryMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? null : sameUser.getId();
    }

    private PasswordMemo findExisting(Long ownerUserId, String siteName, String siteUrl, String username) {
        QueryWrapper<PasswordMemo> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", ownerUserId)
                .eq("site_name", siteName)
                .eq("site_url", siteUrl);
        if (StringUtils.hasText(username)) {
            wrapper.eq("username", username);
        } else {
            wrapper.isNull("username");
        }
        return passwordMemoMapper.selectOne(wrapper);
    }

    private PasswordMemo copyMemo(PasswordMemo source) {
        PasswordMemo memo = new PasswordMemo();
        DataMigrationBeanMergeSupport.overwrite(source, memo);
        return memo;
    }

    /**
     * 密码备忘录导出载荷。
     */
    public static class Payload {

        private List<PasswordMemo> memos;
        private List<PasswordMemoHistory> histories;

        public Payload() {
        }

        public Payload(List<PasswordMemo> memos, List<PasswordMemoHistory> histories) {
            this.memos = memos;
            this.histories = histories;
        }

        public List<PasswordMemo> getMemos() {
            return memos;
        }

        public void setMemos(List<PasswordMemo> memos) {
            this.memos = memos;
        }

        public List<PasswordMemoHistory> getHistories() {
            return histories;
        }

        public void setHistories(List<PasswordMemoHistory> histories) {
            this.histories = histories;
        }
    }
}
