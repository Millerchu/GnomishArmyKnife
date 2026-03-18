package com.gak.wowcharacter.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WowCharacterServiceTest {

    @Mock
    private WowCharacterMapper wowCharacterMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private WowCharacterService wowCharacterService;

    @Test
    void createShouldNormalizeOptionalFieldsAndDefaultMythicValues() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
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
        assertEquals(101L, result.getId());
    }

    @Test
    void createShouldRejectInvalidClassName() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

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
    void createShouldRequireDungeonNameWhenMythicLevelIsPositive() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

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
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

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
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
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
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
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
        assertEquals(2, result.getFactionStats().size());
        assertEquals("部落", result.getFactionStats().get(1).getLabel());
        assertEquals(2, result.getRealmStats().get(0).getCount());
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
}
