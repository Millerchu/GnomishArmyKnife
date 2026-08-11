package com.gak.wowcharacter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.domain.WowCharacter;
import com.gak.wowcharacter.domain.WowCharacterKeybinding;
import com.gak.wowcharacter.domain.WowCharacterMacro;
import com.gak.wowcharacter.domain.WowCharacterMythicRun;
import com.gak.wowcharacter.domain.WowCharacterWeeklyVault;
import com.gak.wowcharacter.dto.SaveWowCharacterKeybindingRequest;
import com.gak.wowcharacter.dto.SaveWowCharacterMacroRequest;
import com.gak.wowcharacter.dto.SaveWowCharacterMythicRunRequest;
import com.gak.wowcharacter.dto.SaveWowCharacterRequest;
import com.gak.wowcharacter.dto.SaveWowCharacterWeeklyVaultRequest;
import com.gak.wowcharacter.dto.WowCharacterOverviewQueryRequest;
import com.gak.wowcharacter.dto.WowCharacterQueryRequest;
import com.gak.wowcharacter.mapper.WowCharacterKeybindingMapper;
import com.gak.wowcharacter.mapper.WowCharacterMacroMapper;
import com.gak.wowcharacter.mapper.WowCharacterMapper;
import com.gak.wowcharacter.mapper.WowCharacterMythicRunMapper;
import com.gak.wowcharacter.mapper.WowCharacterWeeklyVaultMapper;
import com.gak.wowcharacter.vo.ClassStatVO;
import com.gak.wowcharacter.vo.FactionStatVO;
import com.gak.wowcharacter.vo.RealmStatVO;
import com.gak.wowcharacter.vo.WowCharacterKeybindingVO;
import com.gak.wowcharacter.vo.WowCharacterMacroVO;
import com.gak.wowcharacter.vo.WowCharacterListVO;
import com.gak.wowcharacter.vo.WowCharacterMythicRunVO;
import com.gak.wowcharacter.vo.WowCharacterOverviewVO;
import com.gak.wowcharacter.vo.WowCharacterSimpleVO;
import com.gak.wowcharacter.vo.WowCharacterWeeklyVaultVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * WoW 角色服务。
 */
@Service
public class WowCharacterService {

    private static final String APP_CODE = "APP_WOW_CHARACTER";
    private static final String MODULE_CODE = "WOW_CHARACTER";
    private static final String FACTION_FIELD = "faction";
    private static final String CLASS_NAME_FIELD = "className";
    private static final String RACE_NAME_FIELD = "raceName";
    private static final String SPEC_NAME_FIELD = "specName";
    private static final String PROFESSION_PRIMARY_FIELD = "professionPrimary";
    private static final String PROFESSION_SECONDARY_FIELD = "professionSecondary";
    private static final String MYTHIC_DUNGEON_FIELD = "mythicDungeonName";
    private static final String PROFESSION_DICT_CODE = "WOW_PRIMARY_PROFESSION";
    private static final String DEFAULT_FACTION = "ALLIANCE";
    private static final BigDecimal ZERO_DECIMAL = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MAX_CHARACTER_LEVEL = 90;
    private static final int FEATURED_CHARACTER_LIMIT = 4;
    private static final int[] RAID_VAULT_THRESHOLDS = {2, 4, 6};
    private static final int[] MYTHIC_VAULT_THRESHOLDS = {1, 4, 8};
    private static final int[] WORLD_VAULT_THRESHOLDS = {2, 4, 8};
    private static final Comparator<WowCharacter> ID_ORDER = Comparator.comparing(
            WowCharacter::getId,
            Comparator.nullsLast(Long::compareTo)
    );
    private static final Comparator<WowCharacter> DEFAULT_ORDER = buildDefaultOrder();
    private static final Map<String, Comparator<WowCharacter>> SORT_ORDERS = Map.of(
            "faction", textOrder(WowCharacter::getFaction),
            "characterName", textOrder(WowCharacter::getCharacterName),
            "specName", textOrder(WowCharacter::getSpecName),
            "raceName", textOrder(WowCharacter::getRaceName),
            "level", integerOrder(WowCharacter::getLevel),
            "realmName", textOrder(WowCharacter::getRealmName),
            "itemLevel", decimalOrder(WowCharacter::getItemLevel),
            "currentKey", currentKeyOrder(),
            "mythicScore", decimalOrder(WowCharacter::getMythicScore)
    );

    private final WowCharacterMapper wowCharacterMapper;
    private final WowCharacterMythicRunMapper wowCharacterMythicRunMapper;
    private final WowCharacterWeeklyVaultMapper wowCharacterWeeklyVaultMapper;
    private final WowCharacterKeybindingMapper wowCharacterKeybindingMapper;
    private final WowCharacterMacroMapper wowCharacterMacroMapper;
    private final UserMapper userMapper;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;
    private final DataDictionarySupport dataDictionarySupport;
    private final ObjectMapper objectMapper;

    public WowCharacterService(WowCharacterMapper wowCharacterMapper,
                               WowCharacterMythicRunMapper wowCharacterMythicRunMapper,
                               WowCharacterWeeklyVaultMapper wowCharacterWeeklyVaultMapper,
                               WowCharacterKeybindingMapper wowCharacterKeybindingMapper,
                               WowCharacterMacroMapper wowCharacterMacroMapper,
                               UserMapper userMapper,
                               DataDictionaryUsageSupport dataDictionaryUsageSupport,
                               DataDictionarySupport dataDictionarySupport,
                               ObjectMapper objectMapper) {
        this.wowCharacterMapper = wowCharacterMapper;
        this.wowCharacterMythicRunMapper = wowCharacterMythicRunMapper;
        this.wowCharacterWeeklyVaultMapper = wowCharacterWeeklyVaultMapper;
        this.wowCharacterKeybindingMapper = wowCharacterKeybindingMapper;
        this.wowCharacterMacroMapper = wowCharacterMacroMapper;
        this.userMapper = userMapper;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
        this.dataDictionarySupport = dataDictionarySupport;
        this.objectMapper = objectMapper;
    }

    public PagedResult<WowCharacterListVO> page(Long currentUserId, WowCharacterQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<WowCharacter> records = filterCharacters(
                currentUserId, request.getKeyword(), request.getFaction(), request.getClassName(), request.getRealmName()
        );
        records.sort(resolveCharacterOrder(request.getSortField(), request.getSortDirection()));

        long total = records.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<WowCharacter> pageRecords = records.subList((int) fromIndex, (int) toIndex);
        List<Long> pageCharacterIds = extractCharacterIds(pageRecords);
        Map<Long, List<WowCharacterMythicRun>> mythicRunMap = loadMythicRunMap(pageCharacterIds);
        Map<Long, List<WowCharacterWeeklyVault>> weeklyVaultMap = loadWeeklyVaultMap(pageCharacterIds);
        Map<Long, List<WowCharacterKeybinding>> keybindingMap = loadKeybindingMap(pageCharacterIds);
        Map<Long, List<WowCharacterMacro>> macroMap = loadMacroMap(pageCharacterIds);

        List<WowCharacterListVO> list = new ArrayList<>();
        for (WowCharacter record : pageRecords) {
            list.add(toListVO(
                    record,
                    mythicRunMap.getOrDefault(record.getId(), Collections.emptyList()),
                    weeklyVaultMap.getOrDefault(record.getId(), Collections.emptyList()),
                    keybindingMap.getOrDefault(record.getId(), Collections.emptyList()),
                    macroMap.getOrDefault(record.getId(), Collections.emptyList())
            ));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public WowCharacterListVO create(Long currentUserId, SaveWowCharacterRequest request) {
        ensureCurrentUserExists(currentUserId);
        validateFeaturedCharacterLimit(currentUserId, null, request.getIsFeatured());
        NormalizedCharacter normalized = normalizeRequest(request);

        LocalDateTime now = LocalDateTime.now();
        WowCharacter character = new WowCharacter();
        character.setOwnerUserId(currentUserId);
        applyNormalized(character, normalized);
        character.setCreatedAt(now);
        character.setUpdatedAt(now);
        wowCharacterMapper.insert(character);

        syncMythicRuns(currentUserId, character.getId(), normalized.mythicRuns(), now);
        syncWeeklyVaults(currentUserId, character.getId(), normalized.weeklyVaults(), now);
        syncKeybindings(currentUserId, character.getId(), normalized.keybindings(), now);
        syncMacros(currentUserId, character.getId(), normalized.macros(), now);
        return toListVO(character, toDomainMythicRuns(character.getId(), currentUserId, normalized.mythicRuns(), now),
                toDomainWeeklyVaults(character.getId(), currentUserId, normalized.weeklyVaults(), now),
                toDomainKeybindings(character.getId(), currentUserId, normalized.keybindings(), now),
                toDomainMacros(character.getId(), currentUserId, normalized.macros(), now));
    }

    @Transactional
    public WowCharacterListVO update(Long currentUserId, Long id, SaveWowCharacterRequest request) {
        ensureCurrentUserExists(currentUserId);
        WowCharacter current = getOwnedCharacterOrThrow(currentUserId, id);
        validateFeaturedCharacterLimit(currentUserId, current, request.getIsFeatured());
        NormalizedCharacter normalized = normalizeRequest(request);

        LocalDateTime now = LocalDateTime.now();
        applyNormalized(current, normalized);
        current.setUpdatedAt(now);
        wowCharacterMapper.updateById(current);

        syncMythicRuns(currentUserId, current.getId(), normalized.mythicRuns(), now);
        syncWeeklyVaults(currentUserId, current.getId(), normalized.weeklyVaults(), now);
        syncKeybindings(currentUserId, current.getId(), normalized.keybindings(), now);
        syncMacros(currentUserId, current.getId(), normalized.macros(), now);
        return toListVO(current, toDomainMythicRuns(current.getId(), currentUserId, normalized.mythicRuns(), now),
                toDomainWeeklyVaults(current.getId(), currentUserId, normalized.weeklyVaults(), now),
                toDomainKeybindings(current.getId(), currentUserId, normalized.keybindings(), now),
                toDomainMacros(current.getId(), currentUserId, normalized.macros(), now));
    }

    /**
     * 角色详情打开时按国服重置周期初始化低保，并清空上一周期的大秘钥匙。
     */
    @Transactional
    public WowCharacterListVO resetWeeklyProgressIfNeeded(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        WowCharacter current = getOwnedCharacterOrThrow(currentUserId, id);
        List<Long> characterIds = List.of(current.getId());
        List<WowCharacterWeeklyVault> weeklyVaults = loadWeeklyVaultMap(characterIds)
                .getOrDefault(current.getId(), Collections.emptyList());

        if (current.getLevel() == null || current.getLevel() < MAX_CHARACTER_LEVEL) {
            return buildDetailVO(current, characterIds, weeklyVaults);
        }

        LocalDateTime now = LocalDateTime.now(WowWeeklyResetSchedule.CHINA_ZONE_ID);
        LocalDate currentWeekStartDate = WowWeeklyResetSchedule.resolveWeekStartDate(now);
        boolean hasCurrentWeekVault = weeklyVaults.stream()
                .anyMatch(item -> currentWeekStartDate.equals(item.getWeekStartDate()));
        if (!hasCurrentWeekVault) {
            deleteWeeklyVaults(currentUserId, current.getId());
            current.setMythicBestLevel(0);
            current.setMythicDungeonName(null);
            current.setUpdatedAt(now);
            wowCharacterMapper.updateById(current);

            WowCharacterWeeklyVault currentWeekVault = createEmptyWeeklyVault(
                    current.getId(), currentUserId, currentWeekStartDate, now
            );
            wowCharacterWeeklyVaultMapper.insert(currentWeekVault);
            weeklyVaults = List.of(currentWeekVault);
        }
        return buildDetailVO(current, characterIds, weeklyVaults);
    }

    /**
     * 强制重置当前用户所有满级角色的低保与当前钥匙。
     */
    @Transactional
    public long resetAllWeeklyProgress(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        List<WowCharacter> characters = filterCharacters(currentUserId, null, null, null, null);
        if (characters.isEmpty()) {
            return 0L;
        }

        LocalDateTime now = LocalDateTime.now(WowWeeklyResetSchedule.CHINA_ZONE_ID);
        LocalDate currentWeekStartDate = WowWeeklyResetSchedule.resolveWeekStartDate(now);
        long resetCount = 0L;
        for (WowCharacter character : characters) {
            if (character.getLevel() == null || character.getLevel() < MAX_CHARACTER_LEVEL) {
                continue;
            }
            deleteWeeklyVaults(currentUserId, character.getId());
            character.setMythicBestLevel(0);
            character.setMythicDungeonName(null);
            character.setUpdatedAt(now);
            wowCharacterMapper.updateById(character);
            wowCharacterWeeklyVaultMapper.insert(createEmptyWeeklyVault(
                    character.getId(), currentUserId, currentWeekStartDate, now
            ));
            resetCount++;
        }
        return resetCount;
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        WowCharacter current = getOwnedCharacterOrThrow(currentUserId, id);

        QueryWrapper<WowCharacterMythicRun> mythicRunWrapper = new QueryWrapper<>();
        mythicRunWrapper.eq("character_id", current.getId()).eq("owner_user_id", currentUserId);
        wowCharacterMythicRunMapper.delete(mythicRunWrapper);

        deleteWeeklyVaults(currentUserId, current.getId());

        QueryWrapper<WowCharacterKeybinding> keybindingWrapper = new QueryWrapper<>();
        keybindingWrapper.eq("character_id", current.getId()).eq("owner_user_id", currentUserId);
        wowCharacterKeybindingMapper.delete(keybindingWrapper);

        QueryWrapper<WowCharacterMacro> macroWrapper = new QueryWrapper<>();
        macroWrapper.eq("character_id", current.getId()).eq("owner_user_id", currentUserId);
        wowCharacterMacroMapper.delete(macroWrapper);

        wowCharacterMapper.deleteById(current.getId());
    }

    public WowCharacterOverviewVO overview(Long currentUserId, WowCharacterOverviewQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<WowCharacter> records = filterCharacters(
                currentUserId, request.getKeyword(), request.getFaction(), request.getClassName(), null
        );
        records.sort(DEFAULT_ORDER);
        List<Long> characterIds = extractCharacterIds(records);
        Map<Long, List<WowCharacterMythicRun>> mythicRunMap = loadMythicRunMap(characterIds);
        Map<Long, List<WowCharacterWeeklyVault>> weeklyVaultMap = loadWeeklyVaultMap(characterIds);
        Map<Long, List<WowCharacterKeybinding>> keybindingMap = loadKeybindingMap(characterIds);
        Map<Long, List<WowCharacterMacro>> macroMap = loadMacroMap(characterIds);

        WowCharacterOverviewVO overviewVO = new WowCharacterOverviewVO();
        overviewVO.setTotalCharacters(records.size());
        overviewVO.setTotalRealms(countDistinctRealms(records));
        overviewVO.setHighestItemLevel(findHighestItemLevel(records));
        overviewVO.setHighestMythicScore(findHighestMythicScore(records));
        overviewVO.setAverageItemLevel(calculateAverageItemLevel(records));
        overviewVO.setFeaturedCharacters(buildFeaturedCharacters(records, mythicRunMap, weeklyVaultMap, keybindingMap, macroMap));
        overviewVO.setFactionStats(buildFactionStats(records));
        overviewVO.setClassStats(buildClassStats(records));
        overviewVO.setRealmStats(buildRealmStats(records));
        return overviewVO;
    }

    private List<WowCharacter> filterCharacters(Long currentUserId,
                                                 String keyword,
                                                 String faction,
                                                 String className,
                                                 String realmName) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        String normalizedFaction = normalizeOptionalFaction(faction);
        String normalizedClassName = normalizeOptionalClassName(className);
        String trimmedKeyword = trimToNull(keyword);
        String trimmedRealmName = trimToNull(realmName);

        if (normalizedFaction != null) {
            wrapper.eq("faction", normalizedFaction);
        }
        if (normalizedClassName != null) {
            wrapper.eq("class_name", normalizedClassName);
        }
        if (trimmedRealmName != null) {
            wrapper.eq("realm_name", trimmedRealmName);
        }
        if (trimmedKeyword != null) {
            wrapper.and(query -> query.like("character_name", trimmedKeyword)
                    .or()
                    .like("realm_name", trimmedKeyword)
                    .or()
                    .like("race_name", trimmedKeyword)
                    .or()
                    .like("profession_primary", trimmedKeyword)
                    .or()
                    .like("profession_secondary", trimmedKeyword));
        }
        return new ArrayList<>(wowCharacterMapper.selectList(wrapper));
    }

    /**
     * 组装角色详情，避免周重置接口依赖前端持有的旧表单数据。
     */
    private WowCharacterListVO buildDetailVO(WowCharacter character,
                                             List<Long> characterIds,
                                             List<WowCharacterWeeklyVault> weeklyVaults) {
        return toListVO(
                character,
                loadMythicRunMap(characterIds).getOrDefault(character.getId(), Collections.emptyList()),
                weeklyVaults,
                loadKeybindingMap(characterIds).getOrDefault(character.getId(), Collections.emptyList()),
                loadMacroMap(characterIds).getOrDefault(character.getId(), Collections.emptyList())
        );
    }

    /**
     * 删除角色当前保存的低保记录，为新周期空记录腾出唯一键。
     */
    private void deleteWeeklyVaults(Long currentUserId, Long characterId) {
        QueryWrapper<WowCharacterWeeklyVault> weeklyVaultWrapper = new QueryWrapper<>();
        weeklyVaultWrapper.eq("character_id", characterId).eq("owner_user_id", currentUserId);
        wowCharacterWeeklyVaultMapper.delete(weeklyVaultWrapper);
    }

    /**
     * 创建当前周期的空低保记录，三条轨道均从零开始统计。
     */
    private WowCharacterWeeklyVault createEmptyWeeklyVault(Long characterId,
                                                            Long currentUserId,
                                                            LocalDate weekStartDate,
                                                            LocalDateTime now) {
        WowCharacterWeeklyVault weeklyVault = new WowCharacterWeeklyVault();
        weeklyVault.setCharacterId(characterId);
        weeklyVault.setOwnerUserId(currentUserId);
        weeklyVault.setWeekStartDate(weekStartDate);
        weeklyVault.setRaidProgressCount(0);
        weeklyVault.setMythicProgressCount(0);
        weeklyVault.setWorldProgressCount(0);
        weeklyVault.setCreatedAt(now);
        weeklyVault.setUpdatedAt(now);
        return weeklyVault;
    }

    private User ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }

    private WowCharacter getOwnedCharacterOrThrow(Long currentUserId, Long id) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("owner_user_id", currentUserId);
        WowCharacter character = wowCharacterMapper.selectOne(wrapper);
        if (character == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "WoW 角色不存在");
        }
        return character;
    }

    private NormalizedCharacter normalizeRequest(SaveWowCharacterRequest request) {
        ClassOption classOption = normalizeRequiredClassOption(request.getClassName());
        String faction = normalizeRequiredFaction(request.getFaction());
        RaceOption raceOption = normalizeRequiredRaceOption(request.getRaceName());
        SpecOption specOption = normalizeOptionalSpecName(classOption.classCode(), request.getSpecName());
        String professionPrimary = normalizeOptionalProfession(
                PROFESSION_PRIMARY_FIELD,
                request.getProfessionPrimary(),
                "WOW_PROFESSION_PRIMARY_INVALID",
                "professionPrimary 非法"
        );
        String professionSecondary = normalizeOptionalProfession(
                PROFESSION_SECONDARY_FIELD,
                request.getProfessionSecondary(),
                "WOW_PROFESSION_SECONDARY_INVALID",
                "professionSecondary 非法"
        );
        validateProfessionPair(professionPrimary, professionSecondary);
        validateClassRaceRelation(classOption, raceOption);
        validateRaceFactionRelation(raceOption, faction);
        validateClassSpecRelation(classOption, specOption);

        BigDecimal itemLevel = normalizeScaledDecimal(request.getItemLevel(), "itemLevel 不能为空");
        Boolean isFeatured = Boolean.TRUE.equals(request.getIsFeatured());
        BestMythicRun bestMythicRun = normalizeCurrentMythicKey(
                request.getMythicBestLevel(),
                request.getMythicDungeonName()
        );
        List<NormalizedMythicRun> mythicRuns = normalizeMythicRuns(request);
        BigDecimal mythicScore = calculateMythicScore(mythicRuns);
        List<NormalizedWeeklyVault> weeklyVaults = normalizeWeeklyVaults(request.getWeeklyVaults());
        List<NormalizedKeybinding> keybindings = normalizeKeybindings(request.getKeybindings());
        List<NormalizedMacro> macros = normalizeMacros(request.getMacros());

        return new NormalizedCharacter(
                trimRequired(request.getCharacterName(), "characterName 不能为空"),
                classOption.className(),
                specOption != null ? specOption.code() : null,
                raceOption.raceName(),
                trimRequired(request.getRealmName(), "realmName 不能为空"),
                faction,
                request.getLevel(),
                itemLevel,
                isFeatured,
                bestMythicRun.bestTimedLevel(),
                bestMythicRun.dungeonName(),
                mythicScore,
                professionPrimary,
                professionSecondary,
                trimToNull(request.getNote()),
                mythicRuns,
                weeklyVaults,
                keybindings,
                macros
        );
    }

    private List<NormalizedMythicRun> normalizeMythicRuns(SaveWowCharacterRequest request) {
        List<NormalizedMythicRun> result = new ArrayList<>();
        Set<String> dungeonNames = new LinkedHashSet<>();
        if (request.getMythicRuns() != null && !request.getMythicRuns().isEmpty()) {
            for (SaveWowCharacterMythicRunRequest item : request.getMythicRuns()) {
                String dungeonName = normalizeUsageValue(
                        MYTHIC_DUNGEON_FIELD,
                        item.getDungeonName(),
                        true,
                        "WOW_MYTHIC_DUNGEON_INVALID",
                        "mythicRuns.dungeonName 非法"
                );
                if (!dungeonNames.add(dungeonName)) {
                    throw new BusinessException("WOW_MYTHIC_DUNGEON_DUPLICATE", "mythicRuns 存在重复副本");
                }
                result.add(new NormalizedMythicRun(
                        dungeonName,
                        normalizeBestTimedLevel(item.getBestTimedLevel()),
                        normalizeIntegerScore(item.getScore())
                ));
            }
            return result;
        }
        return result;
    }

    private List<NormalizedKeybinding> normalizeKeybindings(List<SaveWowCharacterKeybindingRequest> requestList) {
        if (requestList == null || requestList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, NormalizedKeybinding> result = new LinkedHashMap<>();
        for (SaveWowCharacterKeybindingRequest item : requestList) {
            String bindingName = trimRequired(item.getBindingName(), "keybindings.bindingName 不能为空");
            String normalizedKey = bindingName.toLowerCase(Locale.ROOT);
            if (result.containsKey(normalizedKey)) {
                throw new BusinessException("WOW_KEYBINDING_NAME_DUPLICATE", "keybindings 存在重复方案名称");
            }
            result.put(normalizedKey, new NormalizedKeybinding(
                    bindingName,
                    trimToNull(item.getBindingContent())
            ));
        }
        return new ArrayList<>(result.values());
    }

    /**
     * 宏名称按角色去重，避免游戏内导入时出现名称覆盖。
     */
    private List<NormalizedMacro> normalizeMacros(List<SaveWowCharacterMacroRequest> requestList) {
        if (requestList == null || requestList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, NormalizedMacro> result = new LinkedHashMap<>();
        for (SaveWowCharacterMacroRequest item : requestList) {
            String macroName = trimRequired(item.getMacroName(), "macros.macroName 不能为空");
            String macroContent = trimRequired(item.getMacroContent(), "macros.macroContent 不能为空");
            String normalizedKey = macroName.toLowerCase(Locale.ROOT);
            if (result.containsKey(normalizedKey)) {
                throw new BusinessException("WOW_MACRO_NAME_DUPLICATE", "macros 存在重复宏名称");
            }
            result.put(normalizedKey, new NormalizedMacro(macroName, macroContent));
        }
        return new ArrayList<>(result.values());
    }

    private int normalizeBestTimedLevel(Integer bestTimedLevel) {
        int normalized = bestTimedLevel == null ? 0 : bestTimedLevel;
        if (normalized < 0) {
            throw new BusinessException("WOW_MYTHIC_TIMED_LEVEL_INVALID", "mythicRuns.bestTimedLevel 不能小于 0");
        }
        return normalized;
    }

    private BestMythicRun normalizeCurrentMythicKey(Integer bestLevelValue, String mythicDungeonName) {
        int bestLevel = bestLevelValue == null ? 0 : bestLevelValue;
        if (bestLevel <= 0 && mythicDungeonName == null) {
            return new BestMythicRun(null, 0);
        }
        if (bestLevel > 0 && mythicDungeonName == null) {
            throw new BusinessException("WOW_MYTHIC_DUNGEON_REQUIRED", "mythicBestLevel > 0 时，mythicDungeonName 必填");
        }
        if (mythicDungeonName != null && bestLevel <= 0) {
            throw new BusinessException("WOW_MYTHIC_LEVEL_REQUIRED", "mythicDungeonName 非空时，mythicBestLevel 必须 > 0");
        }
        return new BestMythicRun(
                normalizeUsageValue(
                        MYTHIC_DUNGEON_FIELD,
                        mythicDungeonName,
                        true,
                        "WOW_MYTHIC_DUNGEON_INVALID",
                        "mythicDungeonName 非法"
                ),
                bestLevel
        );
    }

    private List<NormalizedWeeklyVault> normalizeWeeklyVaults(List<SaveWowCharacterWeeklyVaultRequest> requestList) {
        if (requestList == null || requestList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<LocalDate, NormalizedWeeklyVault> result = new LinkedHashMap<>();
        for (SaveWowCharacterWeeklyVaultRequest item : requestList) {
            if (item.getWeekStartDate() == null) {
                throw new BusinessException("WOW_WEEKLY_VAULT_WEEK_REQUIRED", "weekStartDate 不能为空");
            }
            if (result.containsKey(item.getWeekStartDate())) {
                throw new BusinessException("WOW_WEEKLY_VAULT_DUPLICATE_WEEK", "weeklyVaults 存在重复周起始日期");
            }
            result.put(item.getWeekStartDate(), new NormalizedWeeklyVault(
                    item.getId(),
                    item.getWeekStartDate(),
                    normalizeProgressCount(item.getRaidProgressCount()),
                    normalizeProgressCount(item.getMythicProgressCount()),
                    normalizeProgressCount(item.getWorldProgressCount()),
                    trimToNull(item.getNote())
            ));
        }
        return new ArrayList<>(result.values());
    }

    private int normalizeProgressCount(Integer progressCount) {
        return progressCount == null ? 0 : Math.max(progressCount, 0);
    }

    private void applyNormalized(WowCharacter character, NormalizedCharacter normalized) {
        character.setCharacterName(normalized.characterName());
        character.setClassName(normalized.className());
        character.setSpecName(normalized.specName());
        character.setRaceName(normalized.raceName());
        character.setRealmName(normalized.realmName());
        character.setFaction(normalized.faction());
        character.setLevel(normalized.level());
        character.setItemLevel(normalized.itemLevel());
        character.setIsFeatured(normalized.isFeatured());
        character.setMythicBestLevel(normalized.mythicBestLevel());
        character.setMythicDungeonName(normalized.mythicDungeonName());
        character.setMythicScore(normalized.mythicScore());
        character.setProfessionPrimary(normalized.professionPrimary());
        character.setProfessionSecondary(normalized.professionSecondary());
        character.setNote(normalized.note());
    }

    private void syncMythicRuns(Long currentUserId, Long characterId, List<NormalizedMythicRun> normalizedRuns, LocalDateTime now) {
        QueryWrapper<WowCharacterMythicRun> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("character_id", characterId).eq("owner_user_id", currentUserId);
        wowCharacterMythicRunMapper.delete(deleteWrapper);
        for (WowCharacterMythicRun run : toDomainMythicRuns(characterId, currentUserId, normalizedRuns, now)) {
            wowCharacterMythicRunMapper.insert(run);
        }
    }

    private void syncWeeklyVaults(Long currentUserId, Long characterId, List<NormalizedWeeklyVault> normalizedVaults, LocalDateTime now) {
        QueryWrapper<WowCharacterWeeklyVault> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("character_id", characterId).eq("owner_user_id", currentUserId);
        wowCharacterWeeklyVaultMapper.delete(deleteWrapper);
        for (WowCharacterWeeklyVault vault : toDomainWeeklyVaults(characterId, currentUserId, normalizedVaults, now)) {
            wowCharacterWeeklyVaultMapper.insert(vault);
        }
    }

    private void syncKeybindings(Long currentUserId, Long characterId, List<NormalizedKeybinding> normalizedKeybindings, LocalDateTime now) {
        QueryWrapper<WowCharacterKeybinding> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("character_id", characterId).eq("owner_user_id", currentUserId);
        wowCharacterKeybindingMapper.delete(deleteWrapper);
        for (WowCharacterKeybinding keybinding : toDomainKeybindings(characterId, currentUserId, normalizedKeybindings, now)) {
            wowCharacterKeybindingMapper.insert(keybinding);
        }
    }

    private void syncMacros(Long currentUserId, Long characterId, List<NormalizedMacro> normalizedMacros, LocalDateTime now) {
        QueryWrapper<WowCharacterMacro> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("character_id", characterId).eq("owner_user_id", currentUserId);
        wowCharacterMacroMapper.delete(deleteWrapper);
        for (WowCharacterMacro macro : toDomainMacros(characterId, currentUserId, normalizedMacros, now)) {
            wowCharacterMacroMapper.insert(macro);
        }
    }

    private List<WowCharacterMythicRun> toDomainMythicRuns(Long characterId,
                                                           Long currentUserId,
                                                           List<NormalizedMythicRun> normalizedRuns,
                                                           LocalDateTime now) {
        if (normalizedRuns == null || normalizedRuns.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterMythicRun> result = new ArrayList<>();
        for (NormalizedMythicRun item : normalizedRuns) {
            WowCharacterMythicRun run = new WowCharacterMythicRun();
            run.setCharacterId(characterId);
            run.setOwnerUserId(currentUserId);
            run.setDungeonName(item.dungeonName());
            run.setBestTimedLevel(item.bestTimedLevel());
            run.setScore(item.score());
            run.setCreatedAt(now);
            run.setUpdatedAt(now);
            result.add(run);
        }
        return result;
    }

    private List<WowCharacterKeybinding> toDomainKeybindings(Long characterId,
                                                             Long currentUserId,
                                                             List<NormalizedKeybinding> normalizedKeybindings,
                                                             LocalDateTime now) {
        if (normalizedKeybindings == null || normalizedKeybindings.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterKeybinding> result = new ArrayList<>();
        for (NormalizedKeybinding item : normalizedKeybindings) {
            WowCharacterKeybinding keybinding = new WowCharacterKeybinding();
            keybinding.setCharacterId(characterId);
            keybinding.setOwnerUserId(currentUserId);
            keybinding.setBindingName(item.bindingName());
            keybinding.setBindingContent(item.bindingContent());
            keybinding.setCreatedAt(now);
            keybinding.setUpdatedAt(now);
            result.add(keybinding);
        }
        return result;
    }

    private List<WowCharacterMacro> toDomainMacros(Long characterId,
                                                    Long currentUserId,
                                                    List<NormalizedMacro> normalizedMacros,
                                                    LocalDateTime now) {
        if (normalizedMacros == null || normalizedMacros.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterMacro> result = new ArrayList<>();
        for (NormalizedMacro item : normalizedMacros) {
            WowCharacterMacro macro = new WowCharacterMacro();
            macro.setCharacterId(characterId);
            macro.setOwnerUserId(currentUserId);
            macro.setMacroName(item.macroName());
            macro.setMacroContent(item.macroContent());
            macro.setCreatedAt(now);
            macro.setUpdatedAt(now);
            result.add(macro);
        }
        return result;
    }

    private List<WowCharacterWeeklyVault> toDomainWeeklyVaults(Long characterId,
                                                               Long currentUserId,
                                                               List<NormalizedWeeklyVault> normalizedVaults,
                                                               LocalDateTime now) {
        if (normalizedVaults == null || normalizedVaults.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterWeeklyVault> result = new ArrayList<>();
        for (NormalizedWeeklyVault item : normalizedVaults) {
            WowCharacterWeeklyVault vault = new WowCharacterWeeklyVault();
            vault.setId(item.id());
            vault.setCharacterId(characterId);
            vault.setOwnerUserId(currentUserId);
            vault.setWeekStartDate(item.weekStartDate());
            vault.setRaidProgressCount(item.raidProgressCount());
            vault.setMythicProgressCount(item.mythicProgressCount());
            vault.setWorldProgressCount(item.worldProgressCount());
            vault.setNote(item.note());
            vault.setCreatedAt(now);
            vault.setUpdatedAt(now);
            result.add(vault);
        }
        return result;
    }

    private Map<Long, List<WowCharacterMythicRun>> loadMythicRunMap(List<Long> characterIds) {
        if (characterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<WowCharacterMythicRun> wrapper = new QueryWrapper<>();
        wrapper.in("character_id", characterIds).orderByAsc("character_id").orderByAsc("dungeon_name").orderByAsc("id");
        Map<Long, List<WowCharacterMythicRun>> result = new HashMap<>();
        for (WowCharacterMythicRun item : wowCharacterMythicRunMapper.selectList(wrapper)) {
            result.computeIfAbsent(item.getCharacterId(), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<Long, List<WowCharacterWeeklyVault>> loadWeeklyVaultMap(List<Long> characterIds) {
        if (characterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<WowCharacterWeeklyVault> wrapper = new QueryWrapper<>();
        wrapper.in("character_id", characterIds).orderByDesc("week_start_date").orderByDesc("id");
        Map<Long, List<WowCharacterWeeklyVault>> result = new HashMap<>();
        for (WowCharacterWeeklyVault item : wowCharacterWeeklyVaultMapper.selectList(wrapper)) {
            result.computeIfAbsent(item.getCharacterId(), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<Long, List<WowCharacterKeybinding>> loadKeybindingMap(List<Long> characterIds) {
        if (characterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<WowCharacterKeybinding> wrapper = new QueryWrapper<>();
        wrapper.in("character_id", characterIds).orderByAsc("character_id").orderByAsc("binding_name").orderByAsc("id");
        Map<Long, List<WowCharacterKeybinding>> result = new HashMap<>();
        for (WowCharacterKeybinding item : wowCharacterKeybindingMapper.selectList(wrapper)) {
            result.computeIfAbsent(item.getCharacterId(), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<Long, List<WowCharacterMacro>> loadMacroMap(List<Long> characterIds) {
        if (characterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<WowCharacterMacro> wrapper = new QueryWrapper<>();
        wrapper.in("character_id", characterIds).orderByAsc("character_id").orderByAsc("macro_name").orderByAsc("id");
        Map<Long, List<WowCharacterMacro>> result = new HashMap<>();
        for (WowCharacterMacro item : wowCharacterMacroMapper.selectList(wrapper)) {
            result.computeIfAbsent(item.getCharacterId(), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private List<Long> extractCharacterIds(Collection<WowCharacter> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (WowCharacter record : records) {
            ids.add(record.getId());
        }
        return ids;
    }

    private long countDistinctRealms(List<WowCharacter> records) {
        Set<String> realms = new HashSet<>();
        for (WowCharacter record : records) {
            realms.add(record.getRealmName());
        }
        return realms.size();
    }

    private BigDecimal calculateAverageItemLevel(List<WowCharacter> records) {
        if (records.isEmpty()) {
            return ZERO_DECIMAL;
        }
        BigDecimal sum = ZERO_DECIMAL;
        for (WowCharacter record : records) {
            sum = sum.add(safeItemLevel(record));
        }
        return sum.divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal findHighestItemLevel(List<WowCharacter> records) {
        BigDecimal result = ZERO_DECIMAL;
        for (WowCharacter record : records) {
            if (safeItemLevel(record).compareTo(result) > 0) {
                result = safeItemLevel(record);
            }
        }
        return result;
    }

    private BigDecimal findHighestMythicScore(List<WowCharacter> records) {
        BigDecimal result = ZERO_DECIMAL;
        for (WowCharacter record : records) {
            if (safeMythicScore(record).compareTo(result) > 0) {
                result = safeMythicScore(record);
            }
        }
        return result;
    }

    private List<WowCharacterSimpleVO> buildFeaturedCharacters(List<WowCharacter> records,
                                                               Map<Long, List<WowCharacterMythicRun>> mythicRunMap,
                                                               Map<Long, List<WowCharacterWeeklyVault>> weeklyVaultMap,
                                                               Map<Long, List<WowCharacterKeybinding>> keybindingMap,
                                                               Map<Long, List<WowCharacterMacro>> macroMap) {
        List<WowCharacterSimpleVO> result = new ArrayList<>();
        for (WowCharacter record : records.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsFeatured()))
                .sorted(DEFAULT_ORDER)
                .limit(FEATURED_CHARACTER_LIMIT)
                .toList()) {
            result.add(toSimpleVO(
                    record,
                    mythicRunMap.getOrDefault(record.getId(), Collections.emptyList()),
                    weeklyVaultMap.getOrDefault(record.getId(), Collections.emptyList()),
                    keybindingMap.getOrDefault(record.getId(), Collections.emptyList()),
                    macroMap.getOrDefault(record.getId(), Collections.emptyList())
            ));
        }
        return result;
    }

    private List<FactionStatVO> buildFactionStats(List<WowCharacter> records) {
        Map<String, Long> counts = new HashMap<>();
        List<DictionaryOptionVO> factionOptions = dataDictionaryUsageSupport.listEnabledOptionsByUsage(
                APP_CODE,
                MODULE_CODE,
                FACTION_FIELD
        );
        for (DictionaryOptionVO option : factionOptions) {
            counts.put(option.getItemValue(), 0L);
        }
        for (WowCharacter record : records) {
            counts.merge(record.getFaction(), 1L, Long::sum);
        }

        List<FactionStatVO> result = new ArrayList<>();
        long total = records.size();
        for (DictionaryOptionVO option : factionOptions) {
            long count = counts.getOrDefault(option.getItemValue(), 0L);
            FactionStatVO vo = new FactionStatVO();
            vo.setLabel(option.getItemLabel());
            vo.setCount(count);
            vo.setRatio(total == 0 ? 0D : roundDouble((double) count / total));
            result.add(vo);
        }
        return result;
    }

    private List<ClassStatVO> buildClassStats(List<WowCharacter> records) {
        Map<String, List<WowCharacter>> grouped = new HashMap<>();
        for (WowCharacter record : records) {
            grouped.computeIfAbsent(record.getClassName(), key -> new ArrayList<>()).add(record);
        }

        List<ClassStatVO> result = new ArrayList<>();
        for (Map.Entry<String, List<WowCharacter>> entry : grouped.entrySet()) {
            ClassStatVO vo = new ClassStatVO();
            vo.setClassName(entry.getKey());
            vo.setCount(entry.getValue().size());
            vo.setAverageItemLevel(roundDouble(entry.getValue().stream()
                    .map(this::safeItemLevel)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0D)));
            result.add(vo);
        }
        result.sort(Comparator.comparing(ClassStatVO::getCount).reversed()
                .thenComparing(ClassStatVO::getAverageItemLevel, Comparator.reverseOrder())
                .thenComparing(ClassStatVO::getClassName));
        return result;
    }

    private List<RealmStatVO> buildRealmStats(List<WowCharacter> records) {
        Map<String, List<WowCharacter>> grouped = new HashMap<>();
        for (WowCharacter record : records) {
            grouped.computeIfAbsent(record.getRealmName(), key -> new ArrayList<>()).add(record);
        }

        List<RealmStatVO> result = new ArrayList<>();
        for (Map.Entry<String, List<WowCharacter>> entry : grouped.entrySet()) {
            RealmStatVO vo = new RealmStatVO();
            vo.setRealmName(entry.getKey());
            vo.setCount(entry.getValue().size());
            vo.setHighestItemLevel(entry.getValue().stream()
                    .map(this::safeItemLevel)
                    .max(BigDecimal::compareTo)
                    .orElse(ZERO_DECIMAL)
                    .intValue());
            result.add(vo);
        }
        result.sort(Comparator.comparing(RealmStatVO::getCount).reversed()
                .thenComparing(RealmStatVO::getHighestItemLevel, Comparator.reverseOrder())
                .thenComparing(RealmStatVO::getRealmName));
        return result.size() > 5 ? new ArrayList<>(result.subList(0, 5)) : result;
    }

    private WowCharacterListVO toListVO(WowCharacter record,
                                        List<WowCharacterMythicRun> mythicRuns,
                                        List<WowCharacterWeeklyVault> weeklyVaults,
                                        List<WowCharacterKeybinding> keybindings,
                                        List<WowCharacterMacro> macros) {
        ResolvedSpec resolvedSpec = resolveSpecForView(record.getClassName(), record.getSpecName());
        List<WowCharacterMythicRunVO> mythicRunVOs = buildMythicRunVOs(record, mythicRuns);
        WowCharacterListVO vo = new WowCharacterListVO();
        vo.setId(record.getId());
        vo.setCharacterName(record.getCharacterName());
        vo.setClassName(record.getClassName());
        vo.setSpecName(resolvedSpec.value());
        vo.setSpecNameLabel(resolvedSpec.label());
        vo.setRaceName(record.getRaceName());
        vo.setRealmName(record.getRealmName());
        vo.setFaction(record.getFaction());
        vo.setLevel(record.getLevel());
        vo.setItemLevel(safeItemLevel(record));
        vo.setIsFeatured(Boolean.TRUE.equals(record.getIsFeatured()));
        vo.setMythicBestLevel(safeMythicBestLevel(record));
        vo.setMythicDungeonName(record.getMythicDungeonName());
        vo.setMythicScore(safeMythicScore(record));
        vo.setMythicCompletedDungeonCount(countCompletedMythicRuns(mythicRunVOs));
        vo.setProfessionPrimary(record.getProfessionPrimary());
        vo.setProfessionPrimaryLabel(resolveProfessionLabel(record.getProfessionPrimary()));
        vo.setProfessionSecondary(record.getProfessionSecondary());
        vo.setProfessionSecondaryLabel(resolveProfessionLabel(record.getProfessionSecondary()));
        vo.setNote(record.getNote());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setMythicRuns(mythicRunVOs);
        vo.setWeeklyVaults(buildWeeklyVaultVOs(weeklyVaults));
        vo.setKeybindings(buildKeybindingVOs(keybindings));
        vo.setMacros(buildMacroVOs(macros));
        return vo;
    }

    private WowCharacterSimpleVO toSimpleVO(WowCharacter record,
                                            List<WowCharacterMythicRun> mythicRuns,
                                            List<WowCharacterWeeklyVault> weeklyVaults,
                                            List<WowCharacterKeybinding> keybindings,
                                            List<WowCharacterMacro> macros) {
        ResolvedSpec resolvedSpec = resolveSpecForView(record.getClassName(), record.getSpecName());
        List<WowCharacterMythicRunVO> mythicRunVOs = buildMythicRunVOs(record, mythicRuns);
        WowCharacterSimpleVO vo = new WowCharacterSimpleVO();
        vo.setId(record.getId());
        vo.setCharacterName(record.getCharacterName());
        vo.setClassName(record.getClassName());
        vo.setSpecName(resolvedSpec.value());
        vo.setSpecNameLabel(resolvedSpec.label());
        vo.setRaceName(record.getRaceName());
        vo.setRealmName(record.getRealmName());
        vo.setFaction(record.getFaction());
        vo.setLevel(record.getLevel());
        vo.setItemLevel(safeItemLevel(record));
        vo.setIsFeatured(Boolean.TRUE.equals(record.getIsFeatured()));
        vo.setMythicBestLevel(safeMythicBestLevel(record));
        vo.setMythicDungeonName(record.getMythicDungeonName());
        vo.setMythicScore(safeMythicScore(record));
        vo.setMythicCompletedDungeonCount(countCompletedMythicRuns(mythicRunVOs));
        vo.setProfessionPrimary(record.getProfessionPrimary());
        vo.setProfessionPrimaryLabel(resolveProfessionLabel(record.getProfessionPrimary()));
        vo.setProfessionSecondary(record.getProfessionSecondary());
        vo.setProfessionSecondaryLabel(resolveProfessionLabel(record.getProfessionSecondary()));
        vo.setMythicRuns(mythicRunVOs);
        vo.setWeeklyVaults(buildWeeklyVaultVOs(weeklyVaults));
        vo.setKeybindings(buildKeybindingVOs(keybindings));
        vo.setMacros(buildMacroVOs(macros));
        return vo;
    }

    private List<WowCharacterMythicRunVO> buildMythicRunVOs(WowCharacter record, List<WowCharacterMythicRun> mythicRuns) {
        Map<String, WowCharacterMythicRun> runMap = new HashMap<>();
        for (WowCharacterMythicRun item : mythicRuns) {
            runMap.put(item.getDungeonName(), item);
        }

        List<WowCharacterMythicRunVO> result = new ArrayList<>();
        for (DictionaryOptionVO option : dataDictionaryUsageSupport.listEnabledOptionsByUsage(APP_CODE, MODULE_CODE, MYTHIC_DUNGEON_FIELD)) {
            WowCharacterMythicRun item = runMap.get(option.getItemValue());
            WowCharacterMythicRunVO vo = new WowCharacterMythicRunVO();
            vo.setDungeonName(option.getItemValue());
            if (item != null) {
                vo.setBestTimedLevel(item.getBestTimedLevel() == null ? 0 : item.getBestTimedLevel());
                vo.setScore(scale2(item.getScore()));
            } else {
                vo.setBestTimedLevel(0);
                vo.setScore(ZERO_DECIMAL);
            }
            result.add(vo);
        }

        return result;
    }

    private List<WowCharacterKeybindingVO> buildKeybindingVOs(List<WowCharacterKeybinding> keybindings) {
        if (keybindings == null || keybindings.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterKeybindingVO> result = new ArrayList<>();
        for (WowCharacterKeybinding keybinding : keybindings) {
            String bindingContent = trimToNull(keybinding.getBindingContent());
            WowCharacterKeybindingVO vo = new WowCharacterKeybindingVO();
            vo.setBindingName(keybinding.getBindingName());
            vo.setBindingContent(bindingContent);
            vo.setHasKeybinding(bindingContent != null);
            result.add(vo);
        }
        return result;
    }

    private List<WowCharacterMacroVO> buildMacroVOs(List<WowCharacterMacro> macros) {
        if (macros == null || macros.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterMacroVO> result = new ArrayList<>();
        for (WowCharacterMacro macro : macros) {
            WowCharacterMacroVO vo = new WowCharacterMacroVO();
            vo.setMacroName(macro.getMacroName());
            vo.setMacroContent(macro.getMacroContent());
            result.add(vo);
        }
        return result;
    }

    private List<WowCharacterWeeklyVaultVO> buildWeeklyVaultVOs(List<WowCharacterWeeklyVault> weeklyVaults) {
        if (weeklyVaults == null || weeklyVaults.isEmpty()) {
            return Collections.emptyList();
        }
        List<WowCharacterWeeklyVaultVO> result = new ArrayList<>();
        for (WowCharacterWeeklyVault item : weeklyVaults) {
            WowCharacterWeeklyVaultVO vo = new WowCharacterWeeklyVaultVO();
            vo.setId(item.getId());
            vo.setWeekStartDate(item.getWeekStartDate());
            vo.setRaidProgressCount(defaultInt(item.getRaidProgressCount()));
            vo.setMythicProgressCount(defaultInt(item.getMythicProgressCount()));
            vo.setWorldProgressCount(defaultInt(item.getWorldProgressCount()));
            vo.setRaidUnlockedCount(calculateUnlockedCount(defaultInt(item.getRaidProgressCount()), RAID_VAULT_THRESHOLDS));
            vo.setMythicUnlockedCount(calculateUnlockedCount(defaultInt(item.getMythicProgressCount()), MYTHIC_VAULT_THRESHOLDS));
            vo.setWorldUnlockedCount(calculateUnlockedCount(defaultInt(item.getWorldProgressCount()), WORLD_VAULT_THRESHOLDS));
            vo.setNote(item.getNote());
            result.add(vo);
        }
        return result;
    }

    private int countCompletedMythicRuns(List<WowCharacterMythicRunVO> mythicRuns) {
        int count = 0;
        for (WowCharacterMythicRunVO item : mythicRuns) {
            if (item.getScore() != null && item.getScore().compareTo(BigDecimal.ZERO) > 0) {
                count++;
            }
        }
        return count;
    }

    private int calculateUnlockedCount(int progressCount, int[] thresholds) {
        int unlockedCount = 0;
        for (int threshold : thresholds) {
            if (progressCount >= threshold) {
                unlockedCount++;
            }
        }
        return unlockedCount;
    }

    private BigDecimal calculateMythicScore(List<NormalizedMythicRun> mythicRuns) {
        BigDecimal result = ZERO_DECIMAL;
        if (mythicRuns == null) {
            return result;
        }
        for (NormalizedMythicRun item : mythicRuns) {
            result = result.add(item.score());
        }
        return scale2(result);
    }

    private BigDecimal safeItemLevel(WowCharacter record) {
        return scale2(record.getItemLevel());
    }

    private int safeMythicBestLevel(WowCharacter record) {
        return record.getMythicBestLevel() == null ? 0 : record.getMythicBestLevel();
    }

    private BigDecimal safeMythicScore(WowCharacter record) {
        return scale2(record.getMythicScore());
    }

    private BigDecimal normalizeIntegerScore(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("WOW_MYTHIC_SCORE_INVALID", "mythicRuns.score 不能小于 0");
        }
        if (normalized.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("WOW_MYTHIC_SCORE_INVALID", "mythicRuns.score 必须为整数");
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateFeaturedCharacterLimit(Long currentUserId, WowCharacter current, Boolean isFeatured) {
        if (!Boolean.TRUE.equals(isFeatured)) {
            return;
        }
        if (current != null && Boolean.TRUE.equals(current.getIsFeatured())) {
            return;
        }
        if (countFeaturedCharacters(currentUserId) >= FEATURED_CHARACTER_LIMIT) {
            throw new BusinessException("WOW_FEATURED_CHARACTER_LIMIT", "主角色最多只能同时设置 4 个");
        }
    }

    private long countFeaturedCharacters(Long currentUserId) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId).eq("is_featured", true);
        return wowCharacterMapper.selectCount(wrapper);
    }

    private BigDecimal normalizeScaledDecimal(BigDecimal value, String requiredMessage) {
        if (value == null) {
            throw new BusinessException("400", requiredMessage);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("WOW_NUMBER_NEGATIVE", "数值不能小于 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value == null ? ZERO_DECIMAL : value.setScale(2, RoundingMode.HALF_UP);
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("400", message);
        }
        return normalized;
    }

    private String normalizeRequiredFaction(String faction) {
        return normalizeUsageValue(FACTION_FIELD, faction, true, "WOW_FACTION_INVALID", "faction 非法");
    }

    private String normalizeOptionalFaction(String faction) {
        return normalizeUsageValue(FACTION_FIELD, faction, false, "WOW_FACTION_INVALID", "faction 非法");
    }

    private String normalizeOptionalClassName(String className) {
        return normalizeUsageValue(CLASS_NAME_FIELD, className, false, "WOW_CLASS_INVALID", "className 非法");
    }

    private ClassOption normalizeRequiredClassOption(String className) {
        String normalized = normalizeUsageValue(CLASS_NAME_FIELD, className, true, "WOW_CLASS_INVALID", "className 非法");
        ClassOption option = findClassOption(normalized);
        if (option == null) {
            throw new BusinessException("WOW_CLASS_INVALID", "className 非法");
        }
        return option;
    }

    private RaceOption normalizeRequiredRaceOption(String raceName) {
        String normalized = normalizeUsageValue(RACE_NAME_FIELD, raceName, true, "WOW_RACE_INVALID", "raceName 非法");
        RaceOption option = findRaceOption(normalized);
        if (option == null) {
            throw new BusinessException("WOW_RACE_INVALID", "raceName 非法");
        }
        return option;
    }

    private SpecOption normalizeOptionalSpecName(String classCode, String specName) {
        String trimmed = trimToNull(specName);
        if (trimmed == null) {
            return null;
        }
        List<SpecOption> options = listSpecOptions();
        try {
            String normalized = normalizeUsageValue(SPEC_NAME_FIELD, trimmed, true, "WOW_SPEC_INVALID", "specName 非法");
            SpecOption option = findSpecOptionByValue(options, normalized);
            if (option == null) {
                throw new BusinessException("WOW_SPEC_INVALID", "specName 非法");
            }
            return option;
        } catch (BusinessException exception) {
            SpecOption legacyOption = findSpecOptionByLabel(options, classCode, trimmed);
            if (legacyOption != null) {
                return legacyOption;
            }
            throw new BusinessException("WOW_SPEC_INVALID", "specName 非法");
        }
    }

    private String normalizeOptionalProfession(String bizFieldCode,
                                               String profession,
                                               String errorCode,
                                               String message) {
        return normalizeUsageValue(bizFieldCode, profession, false, errorCode, message);
    }

    private void validateProfessionPair(String professionPrimary, String professionSecondary) {
        if (professionPrimary != null && professionPrimary.equalsIgnoreCase(professionSecondary)) {
            throw new BusinessException("WOW_PROFESSION_DUPLICATE", "professionPrimary 与 professionSecondary 不能相同");
        }
    }

    private void validateClassRaceRelation(ClassOption classOption, RaceOption raceOption) {
        if (!raceOption.allowedClassCodes().contains(classOption.classCode())) {
            throw new BusinessException("WOW_CLASS_RACE_MISMATCH", "className 与 raceName 不匹配");
        }
    }

    private void validateRaceFactionRelation(RaceOption raceOption, String faction) {
        if (!raceOption.factions().contains(normalizeFactionCode(faction))) {
            throw new BusinessException("WOW_RACE_FACTION_MISMATCH", "raceName 与 faction 不匹配");
        }
    }

    private void validateClassSpecRelation(ClassOption classOption, SpecOption specOption) {
        if (specOption != null && !classOption.classCode().equals(specOption.classCode())) {
            throw new BusinessException("WOW_CLASS_SPEC_MISMATCH", "className 与 specName 不匹配");
        }
    }

    private ResolvedSpec resolveSpecForView(String className, String specName) {
        String trimmed = trimToNull(specName);
        if (trimmed == null) {
            return new ResolvedSpec(null, null);
        }
        List<SpecOption> options = listSpecOptions();
        SpecOption byValue = findSpecOptionByValue(options, trimmed);
        if (byValue != null) {
            return new ResolvedSpec(byValue.code(), byValue.label());
        }
        ClassOption classOption = findClassOption(className);
        if (classOption != null) {
            SpecOption byLabel = findSpecOptionByLabel(options, classOption.classCode(), trimmed);
            if (byLabel != null) {
                return new ResolvedSpec(byLabel.code(), byLabel.label());
            }
        }
        return new ResolvedSpec(trimmed, trimmed);
    }

    private String resolveProfessionLabel(String profession) {
        String trimmed = trimToNull(profession);
        return trimmed == null ? null : dataDictionarySupport.getLabelByValue(PROFESSION_DICT_CODE, trimmed);
    }

    private String normalizeUsageValue(String bizFieldCode,
                                       String value,
                                       boolean required,
                                       String errorCode,
                                       String message) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(APP_CODE, MODULE_CODE, bizFieldCode, value, required);
        } catch (BusinessException exception) {
            throw new BusinessException(errorCode, message);
        }
    }

    private ClassOption findClassOption(String className) {
        String normalized = trimToNull(className);
        if (normalized == null) {
            return null;
        }
        for (DictionaryOptionVO option : dataDictionaryUsageSupport.listEnabledOptionsByUsage(APP_CODE, MODULE_CODE, CLASS_NAME_FIELD)) {
            if (normalized.equalsIgnoreCase(option.getItemValue())) {
                return new ClassOption(normalizeClassCode(option.getItemCode()), option.getItemValue());
            }
        }
        return null;
    }

    private RaceOption findRaceOption(String raceName) {
        String normalized = trimToNull(raceName);
        if (normalized == null) {
            return null;
        }
        for (DictionaryOptionVO option : dataDictionaryUsageSupport.listEnabledOptionsByUsage(APP_CODE, MODULE_CODE, RACE_NAME_FIELD)) {
            if (normalized.equalsIgnoreCase(option.getItemValue())) {
                return new RaceOption(
                        option.getItemValue(),
                        readNormalizedStringSet(option.getExtraJson(), "factions", true),
                        readNormalizedStringSet(option.getExtraJson(), "allowedClassCodes", false)
                );
            }
        }
        return null;
    }

    private List<SpecOption> listSpecOptions() {
        List<SpecOption> result = new ArrayList<>();
        for (DictionaryOptionVO option : dataDictionaryUsageSupport.listEnabledOptionsByUsage(APP_CODE, MODULE_CODE, SPEC_NAME_FIELD)) {
            result.add(new SpecOption(
                    option.getItemValue(),
                    option.getItemLabel(),
                    readNormalizedStringValue(option.getExtraJson(), "classCode", false)
            ));
        }
        return result;
    }

    private SpecOption findSpecOptionByValue(List<SpecOption> options, String specValue) {
        String normalized = trimToNull(specValue);
        if (normalized == null) {
            return null;
        }
        for (SpecOption option : options) {
            if (normalized.equalsIgnoreCase(option.code())) {
                return option;
            }
        }
        return null;
    }

    private SpecOption findSpecOptionByLabel(List<SpecOption> options, String classCode, String specLabel) {
        String normalizedLabel = trimToNull(specLabel);
        if (normalizedLabel == null || classCode == null) {
            return null;
        }
        for (SpecOption option : options) {
            if (classCode.equals(option.classCode()) && normalizedLabel.equalsIgnoreCase(option.label())) {
                return option;
            }
        }
        return null;
    }

    private Set<String> readNormalizedStringSet(String extraJson, String fieldName, boolean upperCase) {
        JsonNode root = readExtraJson(extraJson);
        if (root == null || !root.has(fieldName) || !root.get(fieldName).isArray()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (JsonNode item : root.get(fieldName)) {
            if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                result.add(upperCase ? normalizeFactionCode(item.asText()) : normalizeClassCode(item.asText()));
            }
        }
        return result;
    }

    private String readNormalizedStringValue(String extraJson, String fieldName, boolean upperCase) {
        JsonNode root = readExtraJson(extraJson);
        if (root == null || !root.has(fieldName) || !root.get(fieldName).isTextual()) {
            return null;
        }
        return upperCase ? normalizeFactionCode(root.get(fieldName).asText()) : normalizeClassCode(root.get(fieldName).asText());
    }

    private JsonNode readExtraJson(String extraJson) {
        String normalized = trimToNull(extraJson);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("WOW_DICTIONARY_METADATA_INVALID", "WoW 字典元数据非法");
        }
    }

    private String normalizeClassCode(String classCode) {
        return classCode == null ? null : classCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFactionCode(String faction) {
        return faction == null ? DEFAULT_FACTION : faction.trim().toUpperCase(Locale.ROOT);
    }

    private static Comparator<WowCharacter> resolveCharacterOrder(String sortField, String sortDirection) {
        if (!StringUtils.hasText(sortField) || !StringUtils.hasText(sortDirection)) {
            return DEFAULT_ORDER;
        }
        Comparator<WowCharacter> requestedOrder = SORT_ORDERS.get(sortField.trim());
        String normalizedDirection = sortDirection.trim().toUpperCase(Locale.ROOT);
        if (requestedOrder == null || !("ASC".equals(normalizedDirection) || "DESC".equals(normalizedDirection))) {
            return DEFAULT_ORDER;
        }
        Comparator<WowCharacter> directedOrder = "DESC".equals(normalizedDirection)
                ? requestedOrder.reversed()
                : requestedOrder;
        return directedOrder.thenComparing(DEFAULT_ORDER);
    }

    private static Comparator<WowCharacter> buildDefaultOrder() {
        Comparator<WowCharacter> order = (left, right) -> {
            int itemLevelCompare = compareDecimalDesc(left.getItemLevel(), right.getItemLevel());
            if (itemLevelCompare != 0) {
                return itemLevelCompare;
            }
            int mythicScoreCompare = compareDecimalDesc(left.getMythicScore(), right.getMythicScore());
            if (mythicScoreCompare != 0) {
                return mythicScoreCompare;
            }
            return safeText(left.getCharacterName()).compareTo(safeText(right.getCharacterName()));
        };
        return order.thenComparing(ID_ORDER);
    }

    private static Comparator<WowCharacter> textOrder(Function<WowCharacter, String> extractor) {
        return Comparator.comparing(character -> safeText(extractor.apply(character)));
    }

    private static Comparator<WowCharacter> integerOrder(Function<WowCharacter, Integer> extractor) {
        return Comparator.comparingInt(character -> valueOrZero(extractor.apply(character)));
    }

    private static Comparator<WowCharacter> decimalOrder(Function<WowCharacter, BigDecimal> extractor) {
        return Comparator.comparing(character -> scaleStatic(extractor.apply(character)));
    }

    private static Comparator<WowCharacter> currentKeyOrder() {
        return integerOrder(WowCharacter::getMythicBestLevel)
                .thenComparing(textOrder(WowCharacter::getMythicDungeonName));
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static int compareDecimalDesc(BigDecimal left, BigDecimal right) {
        return scaleStatic(right).compareTo(scaleStatic(left));
    }

    private static BigDecimal scaleStatic(BigDecimal value) {
        return value == null ? ZERO_DECIMAL : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record NormalizedCharacter(
            String characterName,
            String className,
            String specName,
            String raceName,
            String realmName,
            String faction,
            Integer level,
            BigDecimal itemLevel,
            Boolean isFeatured,
            Integer mythicBestLevel,
            String mythicDungeonName,
            BigDecimal mythicScore,
            String professionPrimary,
            String professionSecondary,
            String note,
            List<NormalizedMythicRun> mythicRuns,
            List<NormalizedWeeklyVault> weeklyVaults,
            List<NormalizedKeybinding> keybindings,
            List<NormalizedMacro> macros
    ) {
    }

    private record NormalizedMythicRun(
            String dungeonName,
            Integer bestTimedLevel,
            BigDecimal score
    ) {
    }

    private record NormalizedWeeklyVault(
            Long id,
            LocalDate weekStartDate,
            Integer raidProgressCount,
            Integer mythicProgressCount,
            Integer worldProgressCount,
            String note
    ) {
    }

    private record NormalizedKeybinding(
            String bindingName,
            String bindingContent
    ) {
    }

    private record NormalizedMacro(
            String macroName,
            String macroContent
    ) {
    }

    private record BestMythicRun(String dungeonName, Integer bestTimedLevel) {
    }

    private record ClassOption(String classCode, String className) {
    }

    private record RaceOption(String raceName, Set<String> factions, Set<String> allowedClassCodes) {
    }

    private record SpecOption(String code, String label, String classCode) {
    }

    private record ResolvedSpec(String value, String label) {
    }
}
