package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统用户迁移处理器。
 */
@Service
public class SystemUsersMigrationHandler implements MigrationResourceHandler {

    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public SystemUsersMigrationHandler(UserMapper userMapper,
                                       DataMigrationArchiveService archiveService) {
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.SYSTEM_RESOURCE_USERS;
    }

    @Override
    public String resourceName() {
        return "用户与账号";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_SYSTEM;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "system/users.json";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("created_at").orderByAsc("id");
        List<User> users = userMapper.selectList(wrapper);
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(users), users.size(), 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        for (User source : payload.getUsers()) {
            if (source == null || source.getUsername() == null || source.getUsername().trim().isEmpty()) {
                continue;
            }
            User existingByUsername = findByUsername(source.getUsername());
            if (existingByUsername != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_USER_CONFLICT", "用户已存在: " + source.getUsername());
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existingByUsername, "id");
                } else {
                    DataMigrationBeanMergeSupport.mergeNonNull(source, existingByUsername, "id");
                }
                userMapper.updateById(existingByUsername);
                context.mapUserId(source.getId(), existingByUsername.getId());
                importedCount++;
                continue;
            }

            User insertUser = copyUser(source);
            if (insertUser.getId() != null && userMapper.selectById(insertUser.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_USER_ID_CONFLICT", "用户 ID 冲突: " + insertUser.getId());
                }
                insertUser.setId(null);
            }
            userMapper.insert(insertUser);
            context.mapUserId(source.getId(), insertUser.getId());
            importedCount++;
        }
        return MigrationResourceImportResult.success(importedCount, 0L, "用户与账号导入完成");
    }

    private User findByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        return userMapper.selectOne(wrapper);
    }

    private User copyUser(User source) {
        User user = new User();
        DataMigrationBeanMergeSupport.overwrite(source, user);
        return user;
    }

    /**
     * 用户导出载荷。
     */
    public static class Payload {

        private List<User> users;

        public Payload() {
        }

        public Payload(List<User> users) {
            this.users = users;
        }

        public List<User> getUsers() {
            return users;
        }

        public void setUsers(List<User> users) {
            this.users = users;
        }
    }
}
