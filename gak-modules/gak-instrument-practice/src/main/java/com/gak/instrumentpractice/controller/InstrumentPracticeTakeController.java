package com.gak.instrumentpractice.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.instrumentpractice.dto.SaveInstrumentPracticeTakeRequest;
import com.gak.instrumentpractice.service.InstrumentPracticeTakeService;
import com.gak.instrumentpractice.vo.InstrumentPracticeTakeVO;
import com.gak.instrumentpractice.vo.SaveInstrumentPracticeTakeResultVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 随身乐器练习片段接口。
 */
@RestController
@RequestMapping("/instrument-practice/takes")
public class InstrumentPracticeTakeController {

    private final InstrumentPracticeTakeService instrumentPracticeTakeService;
    private final TokenService tokenService;

    public InstrumentPracticeTakeController(InstrumentPracticeTakeService instrumentPracticeTakeService,
                                            TokenService tokenService) {
        this.instrumentPracticeTakeService = instrumentPracticeTakeService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<List<InstrumentPracticeTakeVO>> list(HttpServletRequest request) {
        return ApiResponse.success(instrumentPracticeTakeService.list(tokenService.requireCurrentUserId(request)));
    }

    @PostMapping
    public ApiResponse<SaveInstrumentPracticeTakeResultVO> create(
            @Valid @RequestBody SaveInstrumentPracticeTakeRequest request,
            HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(instrumentPracticeTakeService.create(currentUserId, request));
    }

    @DeleteMapping("/{takeId}")
    public ApiResponse<Void> delete(@PathVariable Long takeId, HttpServletRequest request) {
        instrumentPracticeTakeService.delete(tokenService.requireCurrentUserId(request), takeId);
        return ApiResponse.success();
    }
}
