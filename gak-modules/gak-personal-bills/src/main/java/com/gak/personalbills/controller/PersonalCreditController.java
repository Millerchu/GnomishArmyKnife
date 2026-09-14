package com.gak.personalbills.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.personalbills.dto.SaveCreditAccountRequest;
import com.gak.personalbills.service.PersonalCreditService;
import com.gak.personalbills.vo.PersonalCreditAccountVO;
import com.gak.personalbills.vo.PersonalCreditSummaryVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 个人信用统计接口，所有操作绑定登录用户。 */
@RestController
@RequestMapping("/personal-bills/credit-accounts")
public class PersonalCreditController {
    private final PersonalCreditService service;
    private final TokenService tokenService;

    public PersonalCreditController(PersonalCreditService service, TokenService tokenService) {
        this.service = service;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PersonalCreditSummaryVO> summary(HttpServletRequest http) {
        return ApiResponse.success(service.summary(tokenService.requireCurrentUserId(http)));
    }

    @PostMapping
    public ApiResponse<PersonalCreditAccountVO> create(@Valid @RequestBody SaveCreditAccountRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.save(tokenService.requireCurrentUserId(http), null, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PersonalCreditAccountVO> update(@PathVariable Long id, @Valid @RequestBody SaveCreditAccountRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.save(tokenService.requireCurrentUserId(http), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(tokenService.requireCurrentUserId(http), id);
        return ApiResponse.success();
    }
}
