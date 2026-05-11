package com.gak.personalbills.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.framework.response.PagedResult;
import com.gak.personalbills.dto.AnnualBudgetQueryRequest;
import com.gak.personalbills.dto.PersonalBillQueryRequest;
import com.gak.personalbills.dto.PersonalBillSummaryQueryRequest;
import com.gak.personalbills.dto.SaveAnnualBudgetRequest;
import com.gak.personalbills.dto.SavePersonalBillRequest;
import com.gak.personalbills.service.PersonalBillService;
import com.gak.personalbills.vo.AnnualBudgetVO;
import com.gak.personalbills.vo.PersonalBillSummaryVO;
import com.gak.personalbills.vo.PersonalBillVO;
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
 * 个人账单控制器。
 */
@RestController
@RequestMapping("/personal-bills")
public class PersonalBillController {

    private final PersonalBillService personalBillService;
    private final TokenService tokenService;

    public PersonalBillController(PersonalBillService personalBillService, TokenService tokenService) {
        this.personalBillService = personalBillService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<PagedResult<PersonalBillVO>> page(@Valid PersonalBillQueryRequest request,
                                                         HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<PersonalBillVO> create(@Valid @RequestBody SavePersonalBillRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PersonalBillVO> update(@PathVariable Long id,
                                              @Valid @RequestBody SavePersonalBillRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.update(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        personalBillService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @GetMapping("/summary")
    public ApiResponse<PersonalBillSummaryVO> summary(@Valid PersonalBillSummaryQueryRequest request,
                                                      HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.getSummary(currentUserId, request));
    }

    @GetMapping("/budgets")
    public ApiResponse<List<AnnualBudgetVO>> listBudgets(@Valid AnnualBudgetQueryRequest request,
                                                         HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.listBudgets(currentUserId, request));
    }

    @PostMapping("/budgets")
    public ApiResponse<AnnualBudgetVO> createBudget(@Valid @RequestBody SaveAnnualBudgetRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.createBudget(currentUserId, request));
    }

    @PutMapping("/budgets/{id}")
    public ApiResponse<AnnualBudgetVO> updateBudget(@PathVariable Long id,
                                                    @Valid @RequestBody SaveAnnualBudgetRequest request,
                                                    HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(personalBillService.updateBudget(currentUserId, id, request));
    }

    @DeleteMapping("/budgets/{id}")
    public ApiResponse<Void> deleteBudget(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        personalBillService.deleteBudget(currentUserId, id);
        return ApiResponse.success();
    }
}
