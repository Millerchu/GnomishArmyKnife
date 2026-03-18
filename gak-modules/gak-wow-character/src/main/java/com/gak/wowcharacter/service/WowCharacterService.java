package com.gak.wowcharacter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.domain.WowCharacter;
import com.gak.wowcharacter.dto.SaveWowCharacterRequest;
import com.gak.wowcharacter.dto.WowCharacterOverviewQueryRequest;
import com.gak.wowcharacter.dto.WowCharacterQueryRequest;
import com.gak.wowcharacter.enums.WowClassName;
import com.gak.wowcharacter.enums.WowFaction;
import com.gak.wowcharacter.enums.WowMythicDungeonName;
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

    private static final Comparator<WowCharacter> DEFAULT_ORDER = Comparator
            .comparing(WowCharacter::getItemLevel, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WowCharacter::getMythicScore, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WowCharacter::getCharacterName, Comparator.nullsLast(String::compareTo));

    private final WowCharacterMapper wowCharacterMapper;
    private final UserMapper userMapper;

    public WowCharacterService(WowCharacterMapper wowCharacterMapper, UserMapper userMapper) {
        this.wowCharacterMapper = wowCharacterMapper;
        this.userMapper = userMapper;
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
        String className = trimToNull(request.getClassName());
        if (!WowClassName.isValid(className)) {
            throw new BusinessException("WOW_CLASS_INVALID", "className 非法");
        }

        String faction = trimToNull(request.getFaction());
        if (!WowFaction.isValid(faction)) {
            throw new BusinessException("WOW_FACTION_INVALID", "faction 非法");
        }

        return new NormalizedCharacter(
                trimToNull(request.getCharacterName()),
                className,
                trimToNull(request.getSpecName()),
                trimToNull(request.getRaceName()),
                trimToNull(request.getRealmName()),
                WowFaction.from(faction).name(),
                request.getLevel(),
                request.getItemLevel(),
                normalizeMythicBestLevel(request.getMythicBestLevel()),
                normalizeMythicDungeonName(request.getMythicBestLevel(), request.getMythicDungeonName()),
                request.getMythicScore() == null ? 0 : request.getMythicScore(),
                trimToNull(request.getProfessionPrimary()),
                trimToNull(request.getProfessionSecondary()),
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
        String trimmed = trimToNull(faction);
        if (trimmed == null) {
            return null;
        }
        if (!WowFaction.isValid(trimmed)) {
            throw new BusinessException("WOW_FACTION_INVALID", "faction 非法");
        }
        return WowFaction.from(trimmed).name();
    }

    private String normalizeOptionalClassName(String className) {
        String trimmed = trimToNull(className);
        if (trimmed == null) {
            return null;
        }
        if (!WowClassName.isValid(trimmed)) {
            throw new BusinessException("WOW_CLASS_INVALID", "className 非法");
        }
        return trimmed;
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
            WowCharacterSimpleVO vo = new WowCharacterSimpleVO();
            vo.setId(record.getId());
            vo.setCharacterName(record.getCharacterName());
            vo.setClassName(record.getClassName());
            vo.setSpecName(record.getSpecName());
            vo.setRaceName(record.getRaceName());
            vo.setRealmName(record.getRealmName());
            vo.setFaction(record.getFaction());
            vo.setLevel(record.getLevel());
            vo.setItemLevel(safeItemLevel(record));
            vo.setMythicBestLevel(safeMythicBestLevel(record));
            vo.setMythicDungeonName(record.getMythicDungeonName());
            vo.setMythicScore(safeMythicScore(record));
            vo.setProfessionPrimary(record.getProfessionPrimary());
            vo.setProfessionSecondary(record.getProfessionSecondary());
            result.add(vo);
        }
        return result;
    }

    private List<FactionStatVO> buildFactionStats(List<WowCharacter> records) {
        Map<String, Long> counts = new HashMap<>();
        for (WowFaction faction : WowFaction.values()) {
            counts.put(faction.name(), 0L);
        }
        for (WowCharacter record : records) {
            counts.computeIfPresent(record.getFaction(), (key, value) -> value + 1);
        }

        List<FactionStatVO> result = new ArrayList<>();
        long total = records.size();
        for (WowFaction faction : WowFaction.values()) {
            long count = counts.get(faction.name());
            FactionStatVO vo = new FactionStatVO();
            vo.setLabel(faction.getLabel());
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
        WowCharacterListVO vo = new WowCharacterListVO();
        vo.setId(record.getId());
        vo.setCharacterName(record.getCharacterName());
        vo.setClassName(record.getClassName());
        vo.setSpecName(record.getSpecName());
        vo.setRaceName(record.getRaceName());
        vo.setRealmName(record.getRealmName());
        vo.setFaction(record.getFaction());
        vo.setLevel(record.getLevel());
        vo.setItemLevel(safeItemLevel(record));
        vo.setMythicBestLevel(safeMythicBestLevel(record));
        vo.setMythicDungeonName(record.getMythicDungeonName());
        vo.setMythicScore(safeMythicScore(record));
        vo.setProfessionPrimary(record.getProfessionPrimary());
        vo.setProfessionSecondary(record.getProfessionSecondary());
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
        if (trimmedDungeonName != null && !WowMythicDungeonName.isValid(trimmedDungeonName)) {
            throw new BusinessException("WOW_MYTHIC_DUNGEON_INVALID", "mythicDungeonName 非法");
        }
        return trimmedDungeonName;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
}
