package com.gak.wowcharacter.controller;

import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.TokenService;
import com.gak.wowcharacter.constant.WowSeasonConstants;
import com.gak.wowcharacter.domain.WowSpecializationGuide;
import com.gak.wowcharacter.mapper.WowSpecializationGuideMapper;
import com.gak.wowcharacter.service.WowSpecializationGuideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WoW 专精指南控制器管理员鉴权测试。
 */
class WowSpecializationGuideControllerTest {

    private WowSpecializationGuideMapper guideMapper;
    private UserMapper userMapper;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        guideMapper = mock(WowSpecializationGuideMapper.class);
        userMapper = mock(UserMapper.class);
        tokenService = new TokenService(userMapper, Duration.ofHours(24), Duration.ofHours(2), 100);
        WowSpecializationGuideService service = new WowSpecializationGuideService(guideMapper, userMapper);
        WowSpecializationGuideController controller = new WowSpecializationGuideController(service, tokenService);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void updateShouldRequireEnabledAdmin() throws Exception {
        User regularUser = buildUser(10L, "USER");
        User adminUser = buildUser(1L, "ADMIN");
        when(userMapper.selectById(10L)).thenReturn(regularUser);
        when(userMapper.selectById(1L)).thenReturn(adminUser);

        WowSpecializationGuide guide = new WowSpecializationGuide();
        guide.setId(1L);
        guide.setSeasonCode(WowSeasonConstants.CURRENT_SEASON_CODE);
        when(guideMapper.selectById(1L)).thenReturn(guide);

        String body = """
                {
                  "mythicTalentBuildName": "Build A",
                  "raidTalentBuildName": "Build B",
                  "statPriority": "Strength",
                  "sourceName": "Wowhead",
                  "sourceUrl": "https://www.wowhead.com/guide"
                }
                """;

        mockMvc.perform(put("/wow-specialization-guides/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        String userToken = "Bearer " + tokenService.issueToken(10L);
        mockMvc.perform(put("/wow-specialization-guides/1")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        String adminToken = "Bearer " + tokenService.issueToken(1L);
        mockMvc.perform(put("/wow-specialization-guides/1")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private User buildUser(Long id, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setRoleCode(roleCode);
        user.setEnabled(true);
        user.setStatus("ENABLED");
        return user;
    }
}
