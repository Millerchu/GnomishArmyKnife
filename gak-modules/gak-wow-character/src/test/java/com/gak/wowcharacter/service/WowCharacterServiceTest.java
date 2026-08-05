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
import com.gak.wowcharacter.vo.WowCharacterListVO;
import com.gak.wowcharacter.vo.WowCharacterOverviewVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private WowCharacterMythicRunMapper wowCharacterMythicRunMapper;

    @Mock
    private WowCharacterWeeklyVaultMapper wowCharacterWeeklyVaultMapper;

    @Mock
    private WowCharacterKeybindingMapper wowCharacterKeybindingMapper;

    @Mock
    private WowCharacterMacroMapper wowCharacterMacroMapper;

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
        lenient().when(dataDictionaryUsageSupport.listEnabledOptionsByUsage("APP_WOW_CHARACTER", "WOW_CHARACTER", "mythicDungeonName"))
                .thenReturn(mythicDungeonOptions());
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
        lenient().when(wowCharacterMythicRunMapper.selectList(any())).thenReturn(List.of());
        lenient().when(wowCharacterWeeklyVaultMapper.selectList(any())).thenReturn(List.of());
        lenient().when(wowCharacterKeybindingMapper.selectList(any())).thenReturn(List.of());
        lenient().when(wowCharacterMacroMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void createShouldCalculateMythicScoreTimedLevelKeybindingsAndPersistChildren() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            WowCharacter character = invocation.getArgument(0);
            character.setId(101L);
            return 1;
        }).when(wowCharacterMapper).insert(any(WowCharacter.class));

        SaveWowCharacterRequest request = buildSaveRequest();
        request.setMythicRuns(List.of(
                buildRun("通天峰", 318, 14),
                buildRun("魔导师平台", 287, 12)
        ));
        request.setMythicBestLevel(14);
        request.setMythicDungeonName("通天峰");
        request.setWeeklyVaults(List.of(buildWeeklyVault(LocalDate.of(2026, 5, 11), 4, 8, 4)));
        request.setKeybindings(List.of(
                buildKeybinding("团本治疗", "YmluZGluZ3MtaG9seQ=="),
                buildKeybinding("大秘境治疗", "")
        ));
        request.setMacros(List.of(buildMacro("爆发", "/cast 神圣复仇者")));

        WowCharacterListVO result = wowCharacterService.create(1L, request);

        ArgumentCaptor<WowCharacter> characterCaptor = ArgumentCaptor.forClass(WowCharacter.class);
        verify(wowCharacterMapper).insert(characterCaptor.capture());
        assertEquals(new BigDecimal("652.34"), characterCaptor.getValue().getItemLevel());
        assertEquals(Boolean.TRUE, characterCaptor.getValue().getIsFeatured());
        assertEquals(new BigDecimal("605.00"), characterCaptor.getValue().getMythicScore());
        assertEquals(14, characterCaptor.getValue().getMythicBestLevel());
        assertEquals("通天峰", characterCaptor.getValue().getMythicDungeonName());
        assertEquals(8, result.getMythicRuns().size());
        assertEquals(14, result.getMythicRuns().stream()
                .filter(item -> "通天峰".equals(item.getDungeonName()))
                .findFirst()
                .orElseThrow()
                .getBestTimedLevel());
        assertEquals(1, result.getWeeklyVaults().size());
        assertEquals(2, result.getWeeklyVaults().get(0).getRaidUnlockedCount());
        assertEquals(2, result.getKeybindings().size());
        assertEquals("团本治疗", result.getKeybindings().get(0).getBindingName());
        assertEquals(Boolean.TRUE, result.getKeybindings().get(0).getHasKeybinding());
        assertEquals("YmluZGluZ3MtaG9seQ==", result.getKeybindings().get(0).getBindingContent());
        assertEquals(Boolean.FALSE, result.getKeybindings().get(1).getHasKeybinding());
        assertEquals(1, result.getMacros().size());
        assertEquals("爆发", result.getMacros().get(0).getMacroName());
        assertEquals("/cast 神圣复仇者", result.getMacros().get(0).getMacroContent());
        assertEquals(Boolean.TRUE, result.getIsFeatured());
        assertEquals(new BigDecimal("605.00"), result.getMythicScore());

        ArgumentCaptor<WowCharacterMythicRun> runCaptor = ArgumentCaptor.forClass(WowCharacterMythicRun.class);
        verify(wowCharacterMythicRunMapper, org.mockito.Mockito.times(2)).insert(runCaptor.capture());
        assertEquals(14, runCaptor.getAllValues().get(0).getBestTimedLevel());

        ArgumentCaptor<WowCharacterKeybinding> keybindingCaptor = ArgumentCaptor.forClass(WowCharacterKeybinding.class);
        verify(wowCharacterKeybindingMapper, org.mockito.Mockito.times(2)).insert(keybindingCaptor.capture());
        assertEquals("团本治疗", keybindingCaptor.getAllValues().get(0).getBindingName());
        assertEquals("YmluZGluZ3MtaG9seQ==", keybindingCaptor.getAllValues().get(0).getBindingContent());

        ArgumentCaptor<WowCharacterMacro> macroCaptor = ArgumentCaptor.forClass(WowCharacterMacro.class);
        verify(wowCharacterMacroMapper).insert(macroCaptor.capture());
        assertEquals("爆发", macroCaptor.getValue().getMacroName());
    }

    @Test
    void createShouldRejectDuplicateMythicDungeon() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SaveWowCharacterRequest request = buildSaveRequest();
        request.setMythicRuns(List.of(
                buildRun("通天峰", 318, 14),
                buildRun("通天峰", 350, 15)
        ));

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_MYTHIC_DUNGEON_DUPLICATE", exception.getCode());
    }

    @Test
    void createShouldRejectDuplicateKeybindingNameIgnoringCase() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SaveWowCharacterRequest request = buildSaveRequest();
        request.setKeybindings(List.of(
                buildKeybinding("Raid", "first"),
                buildKeybinding("raid", "second")
        ));

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));

        assertEquals("WOW_KEYBINDING_NAME_DUPLICATE", exception.getCode());
    }

    @Test
    void createShouldRejectDuplicateMacroNameIgnoringCase() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SaveWowCharacterRequest request = buildSaveRequest();
        request.setMacros(List.of(
                buildMacro("爆发", "/cast 技能一"),
                buildMacro("爆发", "/cast 技能二")
        ));

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));

        assertEquals("WOW_MACRO_NAME_DUPLICATE", exception.getCode());
    }

    @Test
    void pageShouldSortByItemLevelAndMythicScore() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(
                buildCharacter(2L, "B", "战士", "ALLIANCE", "熊猫酒仙", "645.22", "2800.00", false),
                buildCharacter(1L, "A", "法师", "ALLIANCE", "熊猫酒仙", "650.50", "2600.00", false),
                buildCharacter(3L, "C", "牧师", "ALLIANCE", "熊猫酒仙", "650.50", "3000.00", true)
        ));
        when(wowCharacterMythicRunMapper.selectList(any())).thenReturn(List.of(
                buildMythicRun(3L, "通天峰", 350),
                buildMythicRun(1L, "艾杰斯亚学院", 320)
        ));

        WowCharacterQueryRequest request = new WowCharacterQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(10L);
        PagedResult<WowCharacterListVO> result = wowCharacterService.page(1L, request);

        assertEquals(3, result.list().size());
        assertEquals("C", result.list().get(0).getCharacterName());
        assertEquals("A", result.list().get(1).getCharacterName());
        assertEquals("B", result.list().get(2).getCharacterName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "faction", "characterName", "specName", "level",
            "realmName", "itemLevel", "currentKey", "mythicScore"
    })
    void pageShouldSortEverySupportedFieldInBothDirections(String sortField) {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        WowCharacter lower = buildSortableCharacter(
                1L, "A", "ALLIANCE", "discipline", 70,
                "A服", "600.00", 10, "艾杰斯亚学院", "1000.00"
        );
        WowCharacter higher = buildSortableCharacter(
                2L, "B", "HORDE", "frost_mage", 80,
                "B服", "650.00", 12, "通天峰", "2000.00"
        );
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(higher, lower));

        PagedResult<WowCharacterListVO> ascending = wowCharacterService.page(
                1L, buildSortRequest(sortField, " asc ", 1L, 10L)
        );
        PagedResult<WowCharacterListVO> descending = wowCharacterService.page(
                1L, buildSortRequest(sortField, "DESC", 1L, 10L)
        );

        assertCharacterOrder(ascending, "A", "B");
        assertCharacterOrder(descending, "B", "A");
    }

    @Test
    void pageShouldSortCurrentKeyByLevelThenDungeonName() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        WowCharacter academy = buildSortableCharacter(
                1L, "学院", "ALLIANCE", "discipline", 80,
                "A服", "650.00", 12, "艾杰斯亚学院", "2000.00"
        );
        WowCharacter skyreach = buildSortableCharacter(
                2L, "通天", "ALLIANCE", "discipline", 80,
                "A服", "650.00", 12, "通天峰", "2000.00"
        );
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(skyreach, academy));

        PagedResult<WowCharacterListVO> ascending = wowCharacterService.page(
                1L, buildSortRequest("currentKey", "ASC", 1L, 10L)
        );
        PagedResult<WowCharacterListVO> descending = wowCharacterService.page(
                1L, buildSortRequest("currentKey", "DESC", 1L, 10L)
        );

        assertCharacterOrder(ascending, "学院", "通天");
        assertCharacterOrder(descending, "通天", "学院");
    }

    @Test
    void pageShouldFallbackToDefaultOrderForInvalidOrIncompleteSort() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        WowCharacter lower = buildSortableCharacter(
                1L, "A", "ALLIANCE", "discipline", 70,
                "A服", "600.00", 10, "艾杰斯亚学院", "1000.00"
        );
        WowCharacter higher = buildSortableCharacter(
                2L, "B", "HORDE", "frost_mage", 80,
                "B服", "650.00", 12, "通天峰", "2000.00"
        );
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(lower, higher));

        List<WowCharacterQueryRequest> requests = List.of(
                buildSortRequest("unknown", "ASC", 1L, 10L),
                buildSortRequest("characterName", "SIDEWAYS", 1L, 10L),
                buildSortRequest("characterName", null, 1L, 10L),
                buildSortRequest(null, "ASC", 1L, 10L)
        );

        for (WowCharacterQueryRequest request : requests) {
            assertCharacterOrder(wowCharacterService.page(1L, request), "B", "A");
        }
    }

    @Test
    void pageShouldApplyRequestedSortBeforePagination() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        WowCharacter first = buildSortableCharacter(
                1L, "A", "ALLIANCE", "discipline", 80,
                "A服", "600.00", 10, "艾杰斯亚学院", "1000.00"
        );
        WowCharacter second = buildSortableCharacter(
                2L, "B", "ALLIANCE", "discipline", 80,
                "A服", "600.00", 10, "艾杰斯亚学院", "1000.00"
        );
        WowCharacter third = buildSortableCharacter(
                3L, "C", "ALLIANCE", "discipline", 80,
                "A服", "600.00", 10, "艾杰斯亚学院", "1000.00"
        );
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(third, first, second));

        PagedResult<WowCharacterListVO> result = wowCharacterService.page(
                1L, buildSortRequest("characterName", "ASC", 2L, 1L)
        );

        assertEquals(3L, result.total());
        assertCharacterOrder(result, "B");
    }

    @Test
    void overviewShouldReturnFourFeaturedCharacters() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectList(any())).thenReturn(List.of(
                buildCharacter(1L, "安度因", "牧师", "ALLIANCE", "国王之谷", "650.00", "3000.00", true),
                buildCharacter(2L, "希尔瓦娜斯", "猎人", "HORDE", "凤凰之神", "660.25", "2900.00", true),
                buildCharacter(3L, "萨尔", "萨满", "HORDE", "凤凰之神", "640.80", "3100.00", false),
                buildCharacter(4L, "乌瑟尔", "圣骑士", "ALLIANCE", "白银之手", "655.40", "2800.00", true),
                buildCharacter(5L, "吉安娜", "法师", "ALLIANCE", "塞拉摩", "632.10", "2500.00", true)
        ));

        WowCharacterOverviewVO result = wowCharacterService.overview(1L, new WowCharacterOverviewQueryRequest());

        assertEquals(5, result.getTotalCharacters());
        assertEquals(new BigDecimal("660.25"), result.getHighestItemLevel());
        assertEquals(new BigDecimal("3100.00"), result.getHighestMythicScore());
        assertEquals(4, result.getFeaturedCharacters().size());
        assertEquals("希尔瓦娜斯", result.getFeaturedCharacters().get(0).getCharacterName());
        assertEquals("吉安娜", result.getFeaturedCharacters().get(3).getCharacterName());
    }

    @Test
    void createShouldRejectMoreThanFourFeaturedCharacters() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(wowCharacterMapper.selectCount(any())).thenReturn(4L);

        SaveWowCharacterRequest request = buildSaveRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> wowCharacterService.create(1L, request));
        assertEquals("WOW_FEATURED_CHARACTER_LIMIT", exception.getCode());
    }

    private SaveWowCharacterRequest buildSaveRequest() {
        SaveWowCharacterRequest request = new SaveWowCharacterRequest();
        request.setCharacterName("安度因");
        request.setClassName("牧师");
        request.setSpecName("神圣");
        request.setRaceName("人类");
        request.setRealmName("国王之谷");
        request.setFaction("ALLIANCE");
        request.setLevel(90);
        request.setItemLevel(new BigDecimal("652.34"));
        request.setIsFeatured(true);
        request.setProfessionPrimary("附魔");
        request.setProfessionSecondary("裁缝");
        return request;
    }

    private SaveWowCharacterMythicRunRequest buildRun(String dungeonName, int score, int bestTimedLevel) {
        SaveWowCharacterMythicRunRequest request = new SaveWowCharacterMythicRunRequest();
        request.setDungeonName(dungeonName);
        request.setScore(BigDecimal.valueOf(score));
        request.setBestTimedLevel(bestTimedLevel);
        return request;
    }

    private SaveWowCharacterKeybindingRequest buildKeybinding(String bindingName, String bindingContent) {
        SaveWowCharacterKeybindingRequest request = new SaveWowCharacterKeybindingRequest();
        request.setBindingName(bindingName);
        request.setBindingContent(bindingContent);
        return request;
    }

    private SaveWowCharacterMacroRequest buildMacro(String macroName, String macroContent) {
        SaveWowCharacterMacroRequest request = new SaveWowCharacterMacroRequest();
        request.setMacroName(macroName);
        request.setMacroContent(macroContent);
        return request;
    }

    private SaveWowCharacterWeeklyVaultRequest buildWeeklyVault(LocalDate weekStartDate,
                                                                int raidProgressCount,
                                                                int mythicProgressCount,
                                                                int worldProgressCount) {
        SaveWowCharacterWeeklyVaultRequest request = new SaveWowCharacterWeeklyVaultRequest();
        request.setWeekStartDate(weekStartDate);
        request.setRaidProgressCount(raidProgressCount);
        request.setMythicProgressCount(mythicProgressCount);
        request.setWorldProgressCount(worldProgressCount);
        return request;
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
                                        String itemLevel,
                                        String mythicScore,
                                        boolean isFeatured) {
        WowCharacter character = new WowCharacter();
        character.setId(id);
        character.setOwnerUserId(1L);
        character.setCharacterName(name);
        character.setClassName(className);
        character.setRaceName("人类");
        character.setRealmName(realmName);
        character.setFaction(faction);
        character.setLevel(80);
        character.setItemLevel(new BigDecimal(itemLevel));
        character.setMythicBestLevel(12);
        character.setMythicDungeonName("通天峰");
        character.setMythicScore(new BigDecimal(mythicScore));
        character.setIsFeatured(isFeatured);
        character.setCreatedAt(LocalDateTime.now());
        character.setUpdatedAt(LocalDateTime.now());
        return character;
    }

    private WowCharacter buildSortableCharacter(Long id,
                                                 String name,
                                                 String faction,
                                                 String specName,
                                                 int level,
                                                 String realmName,
                                                 String itemLevel,
                                                 int mythicBestLevel,
                                                 String mythicDungeonName,
                                                 String mythicScore) {
        WowCharacter character = buildCharacter(
                id, name, "法师", faction, realmName, itemLevel, mythicScore, false
        );
        character.setSpecName(specName);
        character.setLevel(level);
        character.setMythicBestLevel(mythicBestLevel);
        character.setMythicDungeonName(mythicDungeonName);
        return character;
    }

    private WowCharacterQueryRequest buildSortRequest(String sortField,
                                                      String sortDirection,
                                                      long pageNo,
                                                      long pageSize) {
        WowCharacterQueryRequest request = new WowCharacterQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setSortField(sortField);
        request.setSortDirection(sortDirection);
        return request;
    }

    private void assertCharacterOrder(PagedResult<WowCharacterListVO> result, String... expectedNames) {
        assertEquals(
                List.of(expectedNames),
                result.list().stream().map(WowCharacterListVO::getCharacterName).toList()
        );
    }

    private WowCharacterMythicRun buildMythicRun(Long characterId, String dungeonName, int score) {
        WowCharacterMythicRun run = new WowCharacterMythicRun();
        run.setCharacterId(characterId);
        run.setDungeonName(dungeonName);
        run.setBestTimedLevel(0);
        run.setScore(BigDecimal.valueOf(score).setScale(2));
        return run;
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
                option("blood_elf", "血精灵", "血精灵",
                        "{\"factions\":[\"HORDE\"],\"allowedClassCodes\":[\"hunter\",\"priest\",\"mage\",\"paladin\",\"warrior\"]}"),
                option("orc", "兽人", "兽人",
                        "{\"factions\":[\"HORDE\"],\"allowedClassCodes\":[\"hunter\",\"warrior\",\"shaman\"]}")
        );
    }

    private List<DictionaryOptionVO> specOptions() {
        return List.of(
                option("holy_priest", "神圣", "holy_priest", "{\"classCode\":\"priest\"}"),
                option("discipline", "戒律", "discipline", "{\"classCode\":\"priest\"}"),
                option("holy_paladin", "神圣", "holy_paladin", "{\"classCode\":\"paladin\"}"),
                option("frost_mage", "冰霜", "frost_mage", "{\"classCode\":\"mage\"}")
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
