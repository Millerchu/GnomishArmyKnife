package com.gak.wowcharacter.service;

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
import com.gak.wowcharacter.vo.WowCharacterListVO;
import com.gak.wowcharacter.vo.WowCharacterOverviewVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WowCharacterServiceTest {

    @Mock
    private WowCharacterMapper wowCharacterMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @Mock
    private DataDictionarySupport dataDictionarySupport;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WowCharacterService wowCharacterService;

    @BeforeEach
    void setUp() {
        lenient().when(dataDictionaryUsageSupport.listEnabledOptionsByUsage("APP_WOW_CHARACTER", "WOW_CHARACTER", "className"))
                .thenReturn(classOptions());
        lenient().when(dataDictionaryUsageSupport.listEnabledOptionsByUsage("APP_WOW_CHARACTER", "WOW_CHARACTER", "raceName"))
                .thenReturn(raceOptions());
        lenient().when(dataDictionaryUsageSupport.listEnabledOptionsByUsage("APP_WOW_CHARACTER", "WOW_CHARACTER", "specName"))
                .thenReturn(specOptions());
        lenient().when(dataDictionaryUsageSupport.listEnabledOptionsByUsage("APP_WOW_CHARACTER", "WOW_CHARACTER", "faction"))
                .thenReturn(factionOptions());
        lenient().when(dataDictionaryUsageSupport.normalizeValueByUsage(
                eq("APP_WOW_CHARACTER"),
                eq("WOW_CHARACTER"),
                anyString(),
                any(),
                anyBoolean()
        )).thenAnswer(invocation -> normalizeWowField(
                invocation.getArgument(2),
                (String) invocation.getArgument(3),
                invocation.getArgument(4)
        ));
        lenient().when(dataDictionarySupport.getLabelByValue(eq("WOW_PRIMARY_PROFESSION"), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void createShouldNormalizeOptionalFieldsAndDefaultMythicValues() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        doAnswer(invocation -> {
            WowCharacter character = invocation.getArgument(0);
            character.setId(101L);
            return 1;
        }).when(wowCharacterMapper).insert(any(WowCharacter.class));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("安度因");
        request.setClassName("牧师");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);

        WowCharacterListVO result = wowCharacterService.create(1L, request);

        ArgumentCaptor<WowCharacter> captor = ArgumentCaptor.forClass(WowCharacter.class);
        verify(wowCharacterMapper).insert(captor.capture());
        WowCharacter saved = captor.getValue();
        assertEquals(1L, saved.getOwnerUserId());
        assertEquals(90, saved.getLevel());
        assertEquals(0, saved.getMythicBestLevel());
        assertEquals(0, saved.getMythicScore());
        assertNull(saved.getSpecName());
        assertEquals(101L, result.getId());
        assertNull(result.getSpecNameLabel());
    }

    @Test
    void createShouldNormalizeLegacySpecLabelAndProfessions() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        doAnswer(invocation -> {
            WowCharacter character = invocation.getArgument(0);
            character.setId(102L);
            return 1;
        }).when(wowCharacterMapper).insert(any(WowCharacter.class));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("安度因");
        request.setClassName("牧师");
        request.setSpecName("神圣");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);
        request.setProfessionPrimary("附魔");
        request.setProfessionSecondary("裁缝");

        WowCharacterListVO result = wowCharacterService.create(1L, request);

        ArgumentCaptor<WowCharacter> captor = ArgumentCaptor.forClass(WowCharacter.class);
        verify(wowCharacterMapper).insert(captor.capture());
        assertEquals("holy_priest", captor.getValue().getSpecName());
        assertEquals("附魔", captor.getValue().getProfessionPrimary());
        assertEquals("裁缝", captor.getValue().getProfessionSecondary());
        assertEquals("holy_priest", result.getSpecName());
        assertEquals("神圣", result.getSpecNameLabel());
        assertEquals("附魔", result.getProfessionPrimaryLabel());
        assertEquals("裁缝", result.getProfessionSecondaryLabel());
    }

    @Test
    void createShouldRejectInvalidClassName() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试");
        request.setClassName("剑圣");
        request.setRaceName("兽人");
        request.setRealmName("霜之哀伤");
        request.setFaction("HORDE");
        request.setLevel(90);
        request.setItemLevel(650);

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_CLASS_INVALID", exception.getCode());
    }

    @Test
    void createShouldRejectClassRaceMismatch() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试");
        request.setClassName("恶魔猎手");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_CLASS_RACE_MISMATCH", exception.getCode());
    }

    @Test
    void createShouldAllowVoidElfDemonHunterWithVengeance() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        doAnswer(invocation -> {
            WowCharacter character = invocation.getArgument(0);
            character.setId(103L);
            return 1;
        }).when(wowCharacterMapper).insert(any(WowCharacter.class));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("奥蕾莉亚");
        request.setClassName("恶魔猎手");
        request.setSpecName("vengeance");
        request.setRaceName("虚空精灵");
        request.setRealmName("银月");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(668);

        WowCharacterListVO result = wowCharacterService.create(1L, request);

        ArgumentCaptor<WowCharacter> captor = ArgumentCaptor.forClass(WowCharacter.class);
        verify(wowCharacterMapper).insert(captor.capture());
        assertEquals("虚空精灵", captor.getValue().getRaceName());
        assertEquals("vengeance", captor.getValue().getSpecName());
        assertEquals("vengeance", result.getSpecName());
        assertEquals("复仇", result.getSpecNameLabel());
    }

    @Test
    void createShouldRejectRaceFactionMismatch() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试");
        request.setClassName("牧师");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("HORDE");
        request.setLevel(90);
        request.setItemLevel(650);

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_RACE_FACTION_MISMATCH", exception.getCode());
    }

    @Test
    void createShouldRejectClassSpecMismatch() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试");
        request.setClassName("牧师");
        request.setSpecName("holy_paladin");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_CLASS_SPEC_MISMATCH", exception.getCode());
    }

    @Test
    void createShouldRejectDuplicateProfessions() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试");
        request.setClassName("牧师");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);
        request.setProfessionPrimary("附魔");
        request.setProfessionSecondary("附魔");

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_PROFESSION_DUPLICATE", exception.getCode());
    }

    @Test
    void createShouldRequireDungeonNameWhenMythicLevelIsPositive() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试角色");
        request.setClassName("法师");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);
        request.setMythicBestLevel(12);

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_MYTHIC_DUNGEON_REQUIRED", exception.getCode());
    }

    @Test
    void createShouldRejectDungeonNameWhenMythicLevelIsZero() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));

        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("测试角色");
        request.setClassName("法师");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(650);
        request.setMythicBestLevel(0);
        request.setMythicDungeonName("通天峰");

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_MYTHIC_LEVEL_REQUIRED", exception.getCode());
    }

    @Test
    void pageShouldSortByItemLevelAndMythicScore() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(
                buildCharacter(2L, "B", "战士", "ALLIANCE", "熊猫酒仙", 645, 2800),
                buildCharacter(1L, "A", "法师", "ALLIANCE", "熊猫酒仙", 650, 2600),
                buildCharacter(3L, "C", "牧师", "ALLIANCE", "熊猫酒仙", 650, 3000)
        ));

        WowCharacterQueryRequest request = new WowCharacterQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(10L);
        PagedResult<WowCharacterListVO> result = wowCharacterService.page(1L, request);

        assertEquals(3, result.list().size());
        assertEquals("C", result.list().get(0).getCharacterName());
        assertEquals("通天峰", result.list().get(0).getMythicDungeonName());
        assertEquals("A", result.list().get(1).getCharacterName());
        assertEquals("B", result.list().get(2).getCharacterName());
    }

    @Test
    void overviewShouldAggregateFeaturedAndStats() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(
                buildCharacter(1L, "安度因", "牧师", "ALLIANCE", "国王之谷", 650, 3000),
                buildCharacter(2L, "希尔瓦娜斯", "猎人", "HORDE", "凤凰之神", 660, 2900),
                buildCharacter(3L, "萨尔", "萨满", "HORDE", "凤凰之神", 640, 3100),
                buildCharacter(4L, "乌瑟尔", "圣骑士", "ALLIANCE", "白银之手", 655, 2800)
        ));

        WowCharacterOverviewQueryRequest request = new WowCharacterOverviewQueryRequest();
        WowCharacterOverviewVO result = wowCharacterService.overview(1L, request);

        assertEquals(4, result.getTotalCharacters());
        assertEquals(3, result.getTotalRealms());
        assertEquals(660, result.getHighestItemLevel());
        assertEquals(3100, result.getHighestMythicScore());
        assertEquals(2, result.getFeaturedCharacters().size());
        assertEquals("希尔瓦娜斯", result.getFeaturedCharacters().get(0).getCharacterName());
        assertEquals("风行者之塔", result.getFeaturedCharacters().get(0).getMythicDungeonName());
        assertNull(result.getFeaturedCharacters().get(0).getSpecNameLabel());
        assertEquals(2, result.getFactionStats().size());
        assertEquals("部落", result.getFactionStats().get(1).getLabel());
        assertEquals(2, result.getRealmStats().get(0).getCount());
    }

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private WowCharacter buildCharacter(Long id,
                                        String name,
                                        String className,
                                        String faction,
                                        String realmName,
                                        int itemLevel,
                                        int mythicScore) {
        WowCharacter character = new WowCharacter();
        character.setId(id);
        character.setOwnerUserId(1L);
        character.setCharacterName(name);
        character.setClassName(className);
        character.setRaceName("人类");
        character.setRealmName(realmName);
        character.setFaction(faction);
        character.setLevel(80);
        character.setItemLevel(itemLevel);
        character.setMythicBestLevel(12);
        character.setMythicDungeonName(resolveDungeonName(name));
        character.setMythicScore(mythicScore);
        return character;
    }

    private String resolveDungeonName(String name) {
        return switch (name) {
            case "A" -> "艾杰斯亚学院";
            case "B" -> "魔导师平台";
            case "C" -> "通天峰";
            case "安度因" -> "执政团之座";
            case "希尔瓦娜斯" -> "风行者之塔";
            case "萨尔" -> "萨隆矿坑";
            case "乌瑟尔" -> "节点希纳斯";
            default -> "迈萨拉洞窟";
        };
    }

    private String normalizeWowField(String field, String value, boolean required) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            if (required) {
                throw new BusinessException("DICT_ITEM_VALUE_REQUIRED", "字典值不能为空");
            }
            return null;
        }
        return switch (field) {
            case "className" -> normalizeFromOptions(normalized, classOptions());
            case "raceName" -> normalizeFromOptions(normalized, raceOptions());
            case "specName" -> normalizeFromOptions(normalized, specOptions());
            case "faction" -> normalizeFromOptions(normalized, factionOptions());
            case "mythicDungeonName" -> normalizeFromOptions(normalized, mythicDungeonOptions());
            case "professionPrimary", "professionSecondary" -> normalizeFromOptions(normalized, professionOptions());
            default -> normalized;
        };
    }

    private String normalizeFromOptions(String value, List<DictionaryOptionVO> options) {
        for (DictionaryOptionVO option : options) {
            if (option.getItemValue().equalsIgnoreCase(value)) {
                return option.getItemValue();
            }
        }
        throw new BusinessException("DICT_ITEM_VALUE_INVALID", "字典值非法");
    }

    private List<DictionaryOptionVO> classOptions() {
        return List.of(
                option("priest", "牧师", "牧师", null),
                option("mage", "法师", "法师", null),
                option("demon_hunter", "恶魔猎手", "恶魔猎手", null),
                option("paladin", "圣骑士", "圣骑士", null),
                option("hunter", "猎人", "猎人", null),
                option("shaman", "萨满", "萨满", null),
                option("warrior", "战士", "战士", null)
        );
    }

    private List<DictionaryOptionVO> raceOptions() {
        return List.of(
                option("human", "人类", "人类",
                        "{\"factions\":[\"ALLIANCE\"],\"allowedClassCodes\":[\"priest\",\"mage\",\"paladin\",\"hunter\",\"warrior\"]}"),
                option("void_elf", "虚空精灵", "虚空精灵",
                        "{\"factions\":[\"ALLIANCE\"],\"allowedClassCodes\":[\"death_knight\",\"demon_hunter\",\"hunter\",\"mage\",\"monk\",\"priest\",\"rogue\",\"warlock\",\"warrior\"]}"),
                option("blood_elf", "血精灵", "血精灵",
                        "{\"factions\":[\"HORDE\"],\"allowedClassCodes\":[\"demon_hunter\",\"priest\",\"mage\",\"paladin\",\"hunter\",\"warrior\"]}"),
                option("orc", "兽人", "兽人",
                        "{\"factions\":[\"HORDE\"],\"allowedClassCodes\":[\"hunter\",\"warrior\",\"shaman\"]}")
        );
    }

    private List<DictionaryOptionVO> specOptions() {
        return List.of(
                option("holy_priest", "神圣", "holy_priest", "{\"classCode\":\"priest\"}"),
                option("discipline", "戒律", "discipline", "{\"classCode\":\"priest\"}"),
                option("holy_paladin", "神圣", "holy_paladin", "{\"classCode\":\"paladin\"}"),
                option("frost_mage", "冰霜", "frost_mage", "{\"classCode\":\"mage\"}"),
                option("havoc", "浩劫", "havoc", "{\"classCode\":\"demon_hunter\"}"),
                option("devourer", "Devourer", "devourer", "{\"classCode\":\"demon_hunter\"}"),
                option("vengeance", "复仇", "vengeance", "{\"classCode\":\"demon_hunter\"}")
        );
    }

    private List<DictionaryOptionVO> factionOptions() {
        return List.of(
                option("alliance", "联盟", "ALLIANCE", null),
                option("horde", "部落", "HORDE", null)
        );
    }

    private List<DictionaryOptionVO> professionOptions() {
        return List.of(
                option("enchanting", "附魔", "附魔", null),
                option("tailoring", "裁缝", "裁缝", null),
                option("engineering", "工程学", "工程学", null)
        );
    }

    private List<DictionaryOptionVO> mythicDungeonOptions() {
        return List.of(
                option("magisters_terrace", "魔导师平台", "魔导师平台", null),
                option("mists_of_tirna_scithe", "迈萨拉洞窟", "迈萨拉洞窟", null),
                option("the_nexus", "节点希纳斯", "节点希纳斯", null),
                option("spire_of_the_windrunner", "风行者之塔", "风行者之塔", null),
                option("academy_of_azjkahet", "艾杰斯亚学院", "艾杰斯亚学院", null),
                option("pit_of_saron", "萨隆矿坑", "萨隆矿坑", null),
                option("seat_of_the_triumvirate", "执政团之座", "执政团之座", null),
                option("skyreach", "通天峰", "通天峰", null)
        );
    }

    private DictionaryOptionVO option(String itemCode, String itemLabel, String itemValue, String extraJson) {
        DictionaryOptionVO option = new DictionaryOptionVO();
        option.setItemCode(itemCode);
        option.setItemLabel(itemLabel);
        option.setItemValue(itemValue);
        option.setExtraJson(extraJson);
        return option;
    }
}
