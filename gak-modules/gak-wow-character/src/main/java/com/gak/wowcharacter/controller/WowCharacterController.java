package com.gak.wowcharacter.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.user.service.user.TokenService;
import com.gak.wowcharacter.dto.SaveWowCharacterRequest;
import com.gak.wowcharacter.dto.WowCharacterOverviewQueryRequest;
import com.gak.wowcharacter.dto.WowCharacterQueryRequest;
import com.gak.wowcharacter.service.WowCharacterService;
import com.gak.wowcharacter.vo.WowCharacterListVO;
import com.gak.wowcharacter.vo.WowCharacterOverviewVO;
import com.gak.wowcharacter.vo.WowCharacterMythicSeasonHistoryVO;
import com.gak.wowcharacter.vo.WowSeasonInfoVO;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WoW 角色控制器。
 */
@RestController
@RequestMapping("/wow-characters")
public class WowCharacterController {

    private final WowCharacterService wowCharacterService;
    private final TokenService tokenService;

    public WowCharacterController(WowCharacterService wowCharacterService, TokenService tokenService) {
        this.wowCharacterService = wowCharacterService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<WowCharacterListVO>> page(@Valid WowCharacterQueryRequest request,
                                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<WowCharacterListVO> create(@Valid @RequestBody SaveWowCharacterRequest request,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WowCharacterListVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody SaveWowCharacterRequest request,
                                                  HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.update(currentUserId, id, request));
    }

    /**
     * 打开角色详情时检查国服周重置，并在需要时初始化本周低保。
     */
    @PostMapping("/{id}/weekly-reset")
    public ApiResponse<WowCharacterListVO> resetWeeklyProgress(@PathVariable Long id,
                                                                HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.resetWeeklyProgressIfNeeded(currentUserId, id));
    }

    /**
     * 手动重置当前用户全部满级角色的本周低保。
     */
    @PostMapping("/weekly-reset")
    public ApiResponse<Long> resetAllWeeklyProgress(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.resetAllWeeklyProgress(currentUserId));
    }

    @GetMapping("/season")
    public ApiResponse<WowSeasonInfoVO> currentSeason() {
        return ApiResponse.success(wowCharacterService.currentSeasonInfo());
    }

    /**
     * 版本切换时归档当前成绩，并重置全部满级角色的 M+ 数据。
     */
    @PostMapping("/mythic-season-reset")
    public ApiResponse<Long> resetMythicSeason(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.archiveAndResetMythicSeason(currentUserId));
    }

    @GetMapping("/{id}/mythic-season-history")
    public ApiResponse<List<WowCharacterMythicSeasonHistoryVO>> mythicSeasonHistory(
            @PathVariable Long id,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.listMythicSeasonHistory(currentUserId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        wowCharacterService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/overview")
    public ApiResponse<WowCharacterOverviewVO> overview(@Valid WowCharacterOverviewQueryRequest request,
                                                        HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowCharacterService.overview(currentUserId, request));
    }
}
