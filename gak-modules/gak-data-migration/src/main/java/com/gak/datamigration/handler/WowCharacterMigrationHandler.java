package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.domain.WowCharacter;
import com.gak.wowcharacter.mapper.WowCharacterMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * WOW 角色迁移处理器。
 */
@Service
public class WowCharacterMigrationHandler implements MigrationResourceHandler {

    private static final String APP_CODE = "APP_WOW_CHARACTER";

    private final WowCharacterMapper wowCharacterMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public WowCharacterMigrationHandler(WowCharacterMapper wowCharacterMapper,
                                        UserMapper userMapper,
                                        DataMigrationArchiveService archiveService) {
        this.wowCharacterMapper = wowCharacterMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return APP_CODE;
    }

    @Override
    public String resourceName() {
        return "WOW 角色";
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
        return 130;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("owner_user_id").orderByAsc("character_name").orderByAsc("id");
        List<WowCharacter> characters = wowCharacterMapper.selectList(wrapper);
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(characters), characters.size(), 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        for (WowCharacter source : payload.getCharacters()) {
            if (source == null || !StringUtils.hasText(source.getCharacterName()) || !StringUtils.hasText(source.getRealmName())) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            if (targetUserId == null) {
                throw new BusinessException("DATA_MIGRATION_WOW_USER_MISSING", "WOW 角色依赖的用户不存在: " + source.getOwnerUserId());
            }
            WowCharacter existing = findExisting(targetUserId, source.getCharacterName(), source.getRealmName());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_WOW_CONFLICT",
                            "WOW 角色已存在: " + source.getCharacterName() + "@" + source.getRealmName());
                }
                source.setOwnerUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                wowCharacterMapper.updateById(existing);
                importedCount++;
                continue;
            }
            WowCharacter insertCharacter = copyCharacter(source);
            insertCharacter.setOwnerUserId(targetUserId);
            if (insertCharacter.getId() != null && wowCharacterMapper.selectById(insertCharacter.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_WOW_ID_CONFLICT", "WOW 角色 ID 冲突: " + insertCharacter.getId());
                }
                insertCharacter.setId(null);
            }
            wowCharacterMapper.insert(insertCharacter);
            importedCount++;
        }
        return MigrationResourceImportResult.success(importedCount, 0L, "WOW 角色导入完成");
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? null : sameUser.getId();
    }

    private WowCharacter findExisting(Long ownerUserId, String characterName, String realmName) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", ownerUserId).eq("character_name", characterName).eq("realm_name", realmName);
        return wowCharacterMapper.selectOne(wrapper);
    }

    private WowCharacter copyCharacter(WowCharacter source) {
        WowCharacter character = new WowCharacter();
        DataMigrationBeanMergeSupport.overwrite(source, character);
        return character;
    }

    /**
     * WOW 角色导出载荷。
     */
    public static class Payload {

        private List<WowCharacter> characters;

        public Payload() {
        }

        public Payload(List<WowCharacter> characters) {
            this.characters = characters;
        }

        public List<WowCharacter> getCharacters() {
            return characters;
        }

        public void setCharacters(List<WowCharacter> characters) {
            this.characters = characters;
        }
    }
}
