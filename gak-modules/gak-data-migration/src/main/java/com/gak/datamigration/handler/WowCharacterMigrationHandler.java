package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.domain.WowCharacter;
import com.gak.wowcharacter.domain.WowCharacterKeybinding;
import com.gak.wowcharacter.domain.WowCharacterMythicRun;
import com.gak.wowcharacter.domain.WowCharacterWeeklyVault;
import com.gak.wowcharacter.mapper.WowCharacterKeybindingMapper;
import com.gak.wowcharacter.mapper.WowCharacterMapper;
import com.gak.wowcharacter.mapper.WowCharacterMythicRunMapper;
import com.gak.wowcharacter.mapper.WowCharacterWeeklyVaultMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * WOW 角色迁移处理器。
 */
@Service
public class WowCharacterMigrationHandler implements MigrationResourceHandler {

    private final WowCharacterMapper wowCharacterMapper;
    private final WowCharacterMythicRunMapper mythicRunMapper;
    private final WowCharacterWeeklyVaultMapper weeklyVaultMapper;
    private final WowCharacterKeybindingMapper keybindingMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public WowCharacterMigrationHandler(WowCharacterMapper wowCharacterMapper,
                                        WowCharacterMythicRunMapper mythicRunMapper,
                                        WowCharacterWeeklyVaultMapper weeklyVaultMapper,
                                        WowCharacterKeybindingMapper keybindingMapper,
                                        UserMapper userMapper,
                                        DataMigrationArchiveService archiveService) {
        this.wowCharacterMapper = wowCharacterMapper;
        this.mythicRunMapper = mythicRunMapper;
        this.weeklyVaultMapper = weeklyVaultMapper;
        this.keybindingMapper = keybindingMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.APP_WOW_CHARACTER;
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
        return "business/" + resourceCode() + "/data.json";
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

        QueryWrapper<WowCharacterMythicRun> runWrapper = new QueryWrapper<>();
        runWrapper.orderByAsc("character_id").orderByAsc("dungeon_name").orderByAsc("id");
        List<WowCharacterMythicRun> mythicRuns = mythicRunMapper.selectList(runWrapper);

        QueryWrapper<WowCharacterWeeklyVault> vaultWrapper = new QueryWrapper<>();
        vaultWrapper.orderByAsc("character_id").orderByAsc("week_start_date").orderByAsc("id");
        List<WowCharacterWeeklyVault> weeklyVaults = weeklyVaultMapper.selectList(vaultWrapper);

        QueryWrapper<WowCharacterKeybinding> keybindingWrapper = new QueryWrapper<>();
        keybindingWrapper.orderByAsc("character_id").orderByAsc("spec_name").orderByAsc("id");
        List<WowCharacterKeybinding> keybindings = keybindingMapper.selectList(keybindingWrapper);

        long recordCount = (long) characters.size() + mythicRuns.size() + weeklyVaults.size() + keybindings.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(),
                new Payload(characters, mythicRuns, weeklyVaults, keybindings), recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        Map<Long, Long> characterIdMappings = new LinkedHashMap<>();
        long importedCount = 0L;
        for (WowCharacter source : DataMigrationQuerySupport.emptyIfNull(payload.getCharacters())) {
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
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                wowCharacterMapper.updateById(existing);
                characterIdMappings.put(source.getId(), existing.getId());
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
            characterIdMappings.put(source.getId(), insertCharacter.getId());
            importedCount++;
        }
        importedCount += importMythicRuns(context, DataMigrationQuerySupport.emptyIfNull(payload.getMythicRuns()), characterIdMappings);
        importedCount += importWeeklyVaults(context, DataMigrationQuerySupport.emptyIfNull(payload.getWeeklyVaults()), characterIdMappings);
        importedCount += importKeybindings(context, DataMigrationQuerySupport.emptyIfNull(payload.getKeybindings()), characterIdMappings);
        return MigrationResourceImportResult.success(importedCount, 0L, "WOW 角色导入完成");
    }

    private long importMythicRuns(ImportContext context,
                                  List<WowCharacterMythicRun> mythicRuns,
                                  Map<Long, Long> characterIdMappings) {
        long importedCount = 0L;
        for (WowCharacterMythicRun source : mythicRuns) {
            Long targetCharacterId = characterIdMappings.get(source.getCharacterId());
            if (targetCharacterId == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            WowCharacterMythicRun existing = findExistingRun(targetCharacterId, source.getDungeonName());
            source.setCharacterId(targetCharacterId);
            source.setOwnerUserId(targetUserId);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_WOW_RUN_CONFLICT");
                existing.setCharacterId(targetCharacterId);
                existing.setOwnerUserId(targetUserId);
                mythicRunMapper.updateById(existing);
            } else {
                WowCharacterMythicRun insertRun = copyMythicRun(source);
                insertRun.setCharacterId(targetCharacterId);
                insertRun.setOwnerUserId(targetUserId);
                mythicRunMapper.insert(insertRun);
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importWeeklyVaults(ImportContext context,
                                    List<WowCharacterWeeklyVault> weeklyVaults,
                                    Map<Long, Long> characterIdMappings) {
        long importedCount = 0L;
        for (WowCharacterWeeklyVault source : weeklyVaults) {
            Long targetCharacterId = characterIdMappings.get(source.getCharacterId());
            if (targetCharacterId == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            WowCharacterWeeklyVault existing = findExistingVault(targetCharacterId, source.getWeekStartDate());
            source.setCharacterId(targetCharacterId);
            source.setOwnerUserId(targetUserId);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_WOW_VAULT_CONFLICT");
                existing.setCharacterId(targetCharacterId);
                existing.setOwnerUserId(targetUserId);
                weeklyVaultMapper.updateById(existing);
            } else {
                WowCharacterWeeklyVault insertVault = copyWeeklyVault(source);
                insertVault.setCharacterId(targetCharacterId);
                insertVault.setOwnerUserId(targetUserId);
                weeklyVaultMapper.insert(insertVault);
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importKeybindings(ImportContext context,
                                   List<WowCharacterKeybinding> keybindings,
                                   Map<Long, Long> characterIdMappings) {
        long importedCount = 0L;
        for (WowCharacterKeybinding source : keybindings) {
            Long targetCharacterId = characterIdMappings.get(source.getCharacterId());
            if (targetCharacterId == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            WowCharacterKeybinding existing = findExistingKeybinding(targetCharacterId, source.getSpecName());
            source.setCharacterId(targetCharacterId);
            source.setOwnerUserId(targetUserId);
            if (existing != null) {
                mergeExisting(context, source, existing, "DATA_MIGRATION_WOW_KEYBINDING_CONFLICT");
                existing.setCharacterId(targetCharacterId);
                existing.setOwnerUserId(targetUserId);
                keybindingMapper.updateById(existing);
            } else {
                WowCharacterKeybinding insertKeybinding = copyKeybinding(source);
                insertKeybinding.setCharacterId(targetCharacterId);
                insertKeybinding.setOwnerUserId(targetUserId);
                keybindingMapper.insert(insertKeybinding);
            }
            importedCount++;
        }
        return importedCount;
    }

    private void mergeExisting(ImportContext context, Object source, Object existing, String conflictCode) {
        if (context.isStrict()) {
            throw new BusinessException(conflictCode, "WOW 角色明细已存在");
        }
        if (context.isOverwrite()) {
            DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "characterId", "ownerUserId");
        } else {
            DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "characterId", "ownerUserId");
        }
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

    private WowCharacterMythicRun findExistingRun(Long characterId, String dungeonName) {
        QueryWrapper<WowCharacterMythicRun> wrapper = new QueryWrapper<>();
        wrapper.eq("character_id", characterId).eq("dungeon_name", dungeonName);
        return mythicRunMapper.selectOne(wrapper);
    }

    private WowCharacterWeeklyVault findExistingVault(Long characterId, java.time.LocalDate weekStartDate) {
        QueryWrapper<WowCharacterWeeklyVault> wrapper = new QueryWrapper<>();
        wrapper.eq("character_id", characterId).eq("week_start_date", weekStartDate);
        return weeklyVaultMapper.selectOne(wrapper);
    }

    private WowCharacterKeybinding findExistingKeybinding(Long characterId, String specName) {
        QueryWrapper<WowCharacterKeybinding> wrapper = new QueryWrapper<>();
        wrapper.eq("character_id", characterId).eq("spec_name", specName);
        return keybindingMapper.selectOne(wrapper);
    }

    private WowCharacter copyCharacter(WowCharacter source) {
        WowCharacter character = new WowCharacter();
        DataMigrationBeanMergeSupport.overwrite(source, character);
        return character;
    }

    private WowCharacterMythicRun copyMythicRun(WowCharacterMythicRun source) {
        WowCharacterMythicRun run = new WowCharacterMythicRun();
        DataMigrationBeanMergeSupport.overwrite(source, run);
        return run;
    }

    private WowCharacterWeeklyVault copyWeeklyVault(WowCharacterWeeklyVault source) {
        WowCharacterWeeklyVault vault = new WowCharacterWeeklyVault();
        DataMigrationBeanMergeSupport.overwrite(source, vault);
        return vault;
    }

    private WowCharacterKeybinding copyKeybinding(WowCharacterKeybinding source) {
        WowCharacterKeybinding keybinding = new WowCharacterKeybinding();
        DataMigrationBeanMergeSupport.overwrite(source, keybinding);
        return keybinding;
    }

    /**
     * WOW 角色导出载荷。
     */
    public static class Payload {

        private List<WowCharacter> characters;
        private List<WowCharacterMythicRun> mythicRuns;
        private List<WowCharacterWeeklyVault> weeklyVaults;
        private List<WowCharacterKeybinding> keybindings;

        public Payload() {
        }

        public Payload(List<WowCharacter> characters,
                       List<WowCharacterMythicRun> mythicRuns,
                       List<WowCharacterWeeklyVault> weeklyVaults,
                       List<WowCharacterKeybinding> keybindings) {
            this.characters = characters;
            this.mythicRuns = mythicRuns;
            this.weeklyVaults = weeklyVaults;
            this.keybindings = keybindings;
        }

        public List<WowCharacter> getCharacters() {
            return characters;
        }

        public void setCharacters(List<WowCharacter> characters) {
            this.characters = characters;
        }

        public List<WowCharacterMythicRun> getMythicRuns() {
            return mythicRuns;
        }

        public void setMythicRuns(List<WowCharacterMythicRun> mythicRuns) {
            this.mythicRuns = mythicRuns;
        }

        public List<WowCharacterWeeklyVault> getWeeklyVaults() {
            return weeklyVaults;
        }

        public void setWeeklyVaults(List<WowCharacterWeeklyVault> weeklyVaults) {
            this.weeklyVaults = weeklyVaults;
        }

        public List<WowCharacterKeybinding> getKeybindings() {
            return keybindings;
        }

        public void setKeybindings(List<WowCharacterKeybinding> keybindings) {
            this.keybindings = keybindings;
        }
    }
}
