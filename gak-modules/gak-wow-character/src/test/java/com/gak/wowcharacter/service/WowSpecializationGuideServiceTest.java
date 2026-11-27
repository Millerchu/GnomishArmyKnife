package com.gak.wowcharacter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.wowcharacter.constant.WowSeasonConstants;
import com.gak.wowcharacter.domain.WowSpecializationGuide;
import com.gak.wowcharacter.dto.SaveWowSpecializationGuideRequest;
import com.gak.wowcharacter.mapper.WowSpecializationGuideMapper;
import com.gak.wowcharacter.vo.WowSpecializationGuideVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * WoW 赛季职业专精指南服务测试。
 */
@ExtendWith(MockitoExtension.class)
class WowSpecializationGuideServiceTest {

    @Mock
    private WowSpecializationGuideMapper wowSpecializationGuideMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private WowSpecializationGuideService wowSpecializationGuideService;

    @Test
    void shouldListCurrentSeasonGuides() {
        WowSpecializationGuide guide = buildGuide();
        when(wowSpecializationGuideMapper.selectList(any())).thenReturn(List.of(guide));

        List<WowSpecializationGuideVO> guides = wowSpecializationGuideService.listCurrentSeasonGuides();

        assertEquals(1, guides.size());
        assertEquals("鲜血", guides.getFirst().getSpecName());
        assertEquals("力量 ＞ 急速", guides.getFirst().getStatPriority());
    }

    @Test
    void shouldUpdateEditableGuideFieldsForEnabledAdmin() {
        WowSpecializationGuide guide = buildGuide();
        SaveWowSpecializationGuideRequest request = buildRequest();
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN", true, "ENABLED"));
        when(wowSpecializationGuideMapper.selectById(guide.getId())).thenReturn(guide);

        WowSpecializationGuideVO updated = wowSpecializationGuideService.update(1L, guide.getId(), request);

        ArgumentCaptor<WowSpecializationGuide> captor = ArgumentCaptor.forClass(WowSpecializationGuide.class);
        verify(wowSpecializationGuideMapper).updateById(captor.capture());
        assertEquals("M+ 死亡使者", captor.getValue().getMythicTalentBuildName());
        assertEquals("团本萨莱茵", captor.getValue().getRaidTalentBuildName());
        assertEquals("力量 ＞ 暴击", updated.getStatPriority());
        assertEquals(LocalDate.of(2026, 8, 15), updated.getSourceUpdatedAt());
    }

    @Test
    void shouldRejectMissingGuide() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "ADMIN", true, "ENABLED"));
        when(wowSpecializationGuideMapper.selectById(404L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> wowSpecializationGuideService.update(1L, 404L, buildRequest()));
    }

    @Test
    void shouldRejectNonAdminOrDisabledUserOnUpdate() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "USER", true, "ENABLED"));
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> wowSpecializationGuideService.update(2L, 1L, buildRequest()));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        when(userMapper.selectById(3L)).thenReturn(buildUser(3L, "ADMIN", false, "DISABLED"));
        ResponseStatusException unauthorized = assertThrows(ResponseStatusException.class,
                () -> wowSpecializationGuideService.update(3L, 1L, buildRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());

        verify(wowSpecializationGuideMapper, never()).updateById(any(WowSpecializationGuide.class));
    }

    private User buildUser(Long id, String roleCode, boolean enabled, String status) {
        User user = new User();
        user.setId(id);
        user.setRoleCode(roleCode);
        user.setEnabled(enabled);
        user.setStatus(status);
        return user;
    }

    private WowSpecializationGuide buildGuide() {
        WowSpecializationGuide guide = new WowSpecializationGuide();
        guide.setId(1L);
        guide.setSeasonCode(WowSeasonConstants.CURRENT_SEASON_CODE);
        guide.setClassCode("death_knight");
        guide.setClassName("死亡骑士");
        guide.setSpecCode("blood_death_knight");
        guide.setSpecName("鲜血");
        guide.setRoleType("TANK");
        guide.setSortNo(1);
        guide.setMythicTalentBuildName("M+ 综合");
        guide.setRaidTalentBuildName("团本综合");
        guide.setStatPriority("力量 ＞ 急速");
        guide.setSourceName("Wowhead");
        guide.setSourceUrl("https://www.wowhead.com/");
        guide.setCreatedAt(LocalDateTime.now());
        guide.setUpdatedAt(LocalDateTime.now());
        return guide;
    }

    private SaveWowSpecializationGuideRequest buildRequest() {
        SaveWowSpecializationGuideRequest request = new SaveWowSpecializationGuideRequest();
        request.setMythicTalentBuildName(" M+ 死亡使者 ");
        request.setMythicTalentSummary("大秘境构筑");
        request.setMythicTalentImportCode("mythic-talent-code");
        request.setRaidTalentBuildName("团本萨莱茵");
        request.setRaidTalentSummary("团本单体构筑");
        request.setRaidTalentImportCode("raid-talent-code");
        request.setStatPriority("力量 ＞ 暴击");
        request.setRotationNotes("保持骨盾");
        request.setTrinketRanking("S：毒性装置；A：通用主动饰品");
        request.setSourceName("Wowhead 12.1");
        request.setSourceUrl("https://www.wowhead.com/guide");
        request.setSourceUpdatedAt(LocalDate.of(2026, 8, 15));
        return request;
    }
}
