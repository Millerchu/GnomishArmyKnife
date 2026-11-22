package com.gak.instrumentpractice.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.instrumentpractice.dto.SaveInstrumentPracticeScoreRequest;
import com.gak.instrumentpractice.service.InstrumentPracticeScoreService;
import com.gak.instrumentpractice.vo.InstrumentPracticeScoreVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 随身乐器曲谱管理接口。
 */
@RestController
@RequestMapping("/instrument-practice/scores")
public class InstrumentPracticeScoreController {

    private final InstrumentPracticeScoreService scoreService;
    private final TokenService tokenService;

    public InstrumentPracticeScoreController(InstrumentPracticeScoreService scoreService, TokenService tokenService) {
        this.scoreService = scoreService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<List<InstrumentPracticeScoreVO>> list(HttpServletRequest request) {
        return ApiResponse.success(scoreService.list(tokenService.requireCurrentUserId(request)));
    }

    @PostMapping
    public ApiResponse<InstrumentPracticeScoreVO> create(@Valid @RequestBody SaveInstrumentPracticeScoreRequest score,
                                                         HttpServletRequest request) {
        return ApiResponse.success(scoreService.create(tokenService.requireCurrentUserId(request), score));
    }

    @PutMapping("/{scoreId}")
    public ApiResponse<InstrumentPracticeScoreVO> update(@PathVariable Long scoreId,
                                                         @Valid @RequestBody SaveInstrumentPracticeScoreRequest score,
                                                         HttpServletRequest request) {
        return ApiResponse.success(scoreService.update(tokenService.requireCurrentUserId(request), scoreId, score));
    }

    @DeleteMapping("/{scoreId}")
    public ApiResponse<Void> delete(@PathVariable Long scoreId, HttpServletRequest request) {
        scoreService.delete(tokenService.requireCurrentUserId(request), scoreId);
        return ApiResponse.success();
    }
}
