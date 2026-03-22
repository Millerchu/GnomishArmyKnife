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
import com.gak.wowcharacter.dto.SaveWowCharacterRequest;
import com.gak.wowcharacter.dto.WowCharacterOverviewQueryRequest;
import com.gak.wowcharacter.dto.WowCharacterQueryRequest;
import com.gak.wowcharacter.mapper.WowCharacterMapper;
import com.gak.wowcharacter.vo.ClassStatVO;
import com.gak.wowcharacter.vo.FactionStatVO;
import com.gak.wowcharacter.vo.RealmStatVO;
import com.gak.wowcharacter.vo.WowCharacterListVO;
import com.gak.wowcharacter.vo.WowCharacterOverviewVO;
import com.gak.wowcharacter.vo.WowCharacterSimpleVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final Comparator<WowCharacter> DEFAULT_ORDER = Comparator
            .comparing(WowCharacter::getItemLevel, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WowCharacter::getMythicScore, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WowCharacter::getCharacterName, Comparator.nullsLast(String::compareTo));

    private final WowCharacterMapper wowCharacterMapper;
    private final UserMapper userMapper;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;
    private final DataDictionarySupport dataDictionarySupport;
    private final ObjectMapper objectMapper;

    public WowCharacterService(WowCharacterMapper wowCharacterMapper,
                               UserMapper userMapper,
                               DataDictionaryUsageSupport dataDictionaryUsageSupport,
                               DataDictionarySupport dataDictionarySupport,
                               ObjectMapper objectMapper) {
        this.wowCharacterMapper = wowCharacterMapper;
        this.userMapper = userMapper;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
        this.dataDictionarySupport = dataDictionarySupport;
        this.objectMapper = objectMapper;
    }

    public PagedResult<WowCharacterListVO> page(Long currentUserId, WowCharacterQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<WowCharacter> records = filterCharacters(currentUserId, request.getKeyword(), request.getFaction(), request.getClassName());
        records.sort(DEFAULT_ORDER);

        long total = records.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<WowCharacterListVO> list = new ArrayList<>();
        for (WowCharacter record : records.subList((int) fromIndex, (int) toIndex)) {
            list.add(toListVO(record));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public WowCharacterListVO create(Long currentUserId, SaveWowCharacterRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedCharacter normalized = normalizeRequest(request);

        LocalDateTime now = LocalDateTime.now();
        WowCharacter character = new WowCharacter();
        character.setOwnerUserId(currentUserId);
        applyNormalized(character, normalized);
        character.setCreatedAt(now);
        character.setUpdatedAt(now);
        wowCharacterMapper.insert(character);
        return toListVO(character);
    }

    @Transactional
    public WowCharacterListVO update(Long currentUserId, Long id, SaveWowCharacterRequest request) {
        ensureCurrentUserExists(currentUserId);
        WowCharacter current = getOwnedCharacterOrThrow(currentUserId, id);
        NormalizedCharacter normalized = normalizeRequest(request);

        applyNormalized(current, normalized);
        current.setUpdatedAt(LocalDateTime.now());
        wowCharacterMapper.updateById(current);
        return toListVO(current);
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        WowCharacter current = getOwnedCharacterOrThrow(currentUserId, id);
        wowCharacterMapper.deleteById(current.getId());
    }

    public WowCharacterOverviewVO overview(Long currentUserId, WowCharacterOverviewQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        List<WowCharacter> records = filterCharacters(currentUserId, request.getKeyword(), request.getFaction(), request.getClassName());
        records.sort(DEFAULT_ORDER);

        WowCharacterOverviewVO overviewVO = new WowCharacterOverviewVO();
        overviewVO.setTotalCharacters(records.size());
        overviewVO.setTotalRealms(countDistinctRealms(records));
        overviewVO.setHighestItemLevel(records.stream().mapToInt(this::safeItemLevel).max().orElse(0));
        overviewVO.setHighestMythicScore(records.stream().mapToInt(this::safeMythicScore).max().orElse(0));
        overviewVO.setAverageItemLevel(calculateAverageItemLevel(records));
        overviewVO.setFeaturedCharacters(buildFeaturedCharacters(records));
        overviewVO.setFactionStats(buildFactionStats(records));
        overviewVO.setClassStats(buildClassStats(records));
        overviewVO.setRealmStats(buildRealmStats(records));
        return overviewVO;
    }

    private List<WowCharacter> filterCharacters(Long currentUserId, String keyword, String faction, String className) {
        QueryWrapper<WowCharacter> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        String normalizedFaction = normalizeOptionalFaction(faction);
        String normalizedClassName = normalizeOptionalClassName(className);
        String trimmedKeyword = trimToNull(keyword);

        if (normalizedFaction != null) {
            wrapper.eq("faction", normalizedFaction);
        }
        if (normalizedClassName != null) {
            wrapper.eq("class_name", normalizedClassName);
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

        return new NormalizedCharacter(
                trimToNull(request.getCharacterName()),
                classOption.className(),
                specOption != null ? specOption.code() : null,
                raceOption.raceName(),
                trimToNull(request.getRealmName()),
                faction,
                request.getLevel(),
                request.getItemLevel(),
                normalizeMythicBestLevel(request.getMythicBestLevel()),
                normalizeMythicDungeonName(request.getMythicBestLevel(), request.getMythicDungeonName()),
                request.getMythicScore() == null ? 0 : request.getMythicScore(),
                professionPrimary,
                professionSecondary,
                trimToNull(request.getNote())
        );
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
        character.setMythicBestLevel(normalized.mythicBestLevel());
        character.setMythicDungeonName(normalized.mythicDungeonName());
        character.setMythicScore(normalized.mythicScore());
        character.setProfessionPrimary(normalized.professionPrimary());
        character.setProfessionSecondary(normalized.professionSecondary());
        character.setNote(normalized.note());
    }

    private String normalizeOptionalFaction(String faction) {
        return normalizeUsageValue(FACTION_FIELD, faction, false, "WOW_FACTION_INVALID", "faction 非法");
    }

    private String normalizeOptionalClassName(String className) {
        return normalizeUsageValue(CLASS_NAME_FIELD, className, false, "WOW_CLASS_INVALID", "className 非法");
    }

    private long countDistinctRealms(List<WowCharacter> records) {
        Set<String> realms = new HashSet<>();
        for (WowCharacter record : records) {
            realms.add(record.getRealmName());
        }
        return realms.size();
    }

    private double calculateAverageItemLevel(List<WowCharacter> records) {
        if (records.isEmpty()) {
            return 0D;
        }
        double sum = 0;
        for (WowCharacter record : records) {
            sum += safeItemLevel(record);
        }
        return round(sum / records.size());
    }

    private List<WowCharacterSimpleVO> buildFeaturedCharacters(List<WowCharacter> records) {
        List<WowCharacterSimpleVO> result = new ArrayList<>();
        for (WowCharacter record : records.stream().sorted(DEFAULT_ORDER).limit(2).toList()) {
            ResolvedSpec resolvedSpec = resolveSpecForView(record.getClassName(), record.getSpecName());
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
            vo.setMythicBestLevel(safeMythicBestLevel(record));
            vo.setMythicDungeonName(record.getMythicDungeonName());
            vo.setMythicScore(safeMythicScore(record));
            vo.setProfessionPrimary(record.getProfessionPrimary());
            vo.setProfessionPrimaryLabel(resolveProfessionLabel(record.getProfessionPrimary()));
            vo.setProfessionSecondary(record.getProfessionSecondary());
            vo.setProfessionSecondaryLabel(resolveProfessionLabel(record.getProfessionSecondary()));
            result.add(vo);
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
            vo.setRatio(total == 0 ? 0D : round((double) count / total));
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
            vo.setAverageItemLevel(round(entry.getValue().stream().mapToInt(this::safeItemLevel).average().orElse(0D)));
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
            vo.setHighestItemLevel(entry.getValue().stream().mapToInt(this::safeItemLevel).max().orElse(0));
            result.add(vo);
        }
        result.sort(Comparator.comparing(RealmStatVO::getCount).reversed()
                .thenComparing(RealmStatVO::getHighestItemLevel, Comparator.reverseOrder())
                .thenComparing(RealmStatVO::getRealmName));
        return result.size() > 5 ? new ArrayList<>(result.subList(0, 5)) : result;
    }

    private WowCharacterListVO toListVO(WowCharacter record) {
        ResolvedSpec resolvedSpec = resolveSpecForView(record.getClassName(), record.getSpecName());
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
        vo.setMythicBestLevel(safeMythicBestLevel(record));
        vo.setMythicDungeonName(record.getMythicDungeonName());
        vo.setMythicScore(safeMythicScore(record));
        vo.setProfessionPrimary(record.getProfessionPrimary());
        vo.setProfessionPrimaryLabel(resolveProfessionLabel(record.getProfessionPrimary()));
        vo.setProfessionSecondary(record.getProfessionSecondary());
        vo.setProfessionSecondaryLabel(resolveProfessionLabel(record.getProfessionSecondary()));
        vo.setNote(record.getNote());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private int safeItemLevel(WowCharacter record) {
        return record.getItemLevel() == null ? 0 : record.getItemLevel();
    }

    private int safeMythicBestLevel(WowCharacter record) {
        return record.getMythicBestLevel() == null ? 0 : record.getMythicBestLevel();
    }

    private int safeMythicScore(WowCharacter record) {
        return record.getMythicScore() == null ? 0 : record.getMythicScore();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private int normalizeMythicBestLevel(Integer mythicBestLevel) {
        return mythicBestLevel == null ? 0 : mythicBestLevel;
    }

    private String normalizeMythicDungeonName(Integer mythicBestLevel, String mythicDungeonName) {
        int bestLevel = normalizeMythicBestLevel(mythicBestLevel);
        String trimmedDungeonName = trimToNull(mythicDungeonName);
        if (bestLevel > 0 && trimmedDungeonName == null) {
            throw new BusinessException("WOW_MYTHIC_DUNGEON_REQUIRED", "mythicBestLevel > 0 时，mythicDungeonName 必填");
        }
        if (trimmedDungeonName != null && bestLevel <= 0) {
            throw new BusinessException("WOW_MYTHIC_LEVEL_REQUIRED", "mythicDungeonName 非空时，mythicBestLevel 必须 > 0");
        }
        if (trimmedDungeonName != null) {
            return normalizeUsageValue(
                    MYTHIC_DUNGEON_FIELD,
                    trimmedDungeonName,
                    true,
                    "WOW_MYTHIC_DUNGEON_INVALID",
                    "mythicDungeonName 非法"
            );
        }
        return trimmedDungeonName;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequiredFaction(String faction) {
        return normalizeUsageValue(FACTION_FIELD, faction, true, "WOW_FACTION_INVALID", "faction 非法");
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
            // 兼容旧数据和旧前端仍提交中文专精名的场景，按职业+专精标签归一到当前唯一 code。
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
                // 联动关系直接消费字典 extraJson，避免在服务层维护第二套种族/职业/阵营映射。
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
        return faction == null ? null : faction.trim().toUpperCase(Locale.ROOT);
    }

    private record NormalizedCharacter(
            String characterName,
            String className,
            String specName,
            String raceName,
            String realmName,
            String faction,
            Integer level,
            Integer itemLevel,
            Integer mythicBestLevel,
            String mythicDungeonName,
            Integer mythicScore,
            String professionPrimary,
            String professionSecondary,
            String note
    ) {
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
