package com.gak.wowcharacter.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.user.service.user.TokenService;
import com.gak.wowcharacter.dto.SaveWowSpecializationGuideRequest;
import com.gak.wowcharacter.service.WowSpecializationGuideService;
import com.gak.wowcharacter.vo.WowSpecializationGuideVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WoW 赛季职业专精指南控制器。
 */
@RestController
@RequestMapping("/wow-specialization-guides")
public class WowSpecializationGuideController {

    private final WowSpecializationGuideService wowSpecializationGuideService;
    private final TokenService tokenService;

    public WowSpecializationGuideController(WowSpecializationGuideService wowSpecializationGuideService,
                                            TokenService tokenService) {
        this.wowSpecializationGuideService = wowSpecializationGuideService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<List<WowSpecializationGuideVO>> list(HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowSpecializationGuideService.listCurrentSeasonGuides());
    }

    @PutMapping("/{id}")
    public ApiResponse<WowSpecializationGuideVO> update(@PathVariable Long id,
                                                        @Valid @RequestBody SaveWowSpecializationGuideRequest request,
                                                        HttpServletRequest httpServletRequest) {
        tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(wowSpecializationGuideService.update(id, request));
    }
}
