package com.gak.aisubscription.controller;

import com.gak.aisubscription.dto.*;
import com.gak.aisubscription.service.*;
import com.gak.aisubscription.vo.*;
import com.gak.framework.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai-subscriptions")
public class AiIntegrationController {
    private final AiBudgetService budgets;
    private final AiBillLinkService links;
    private final AiAccessService access;
    public AiIntegrationController(AiBudgetService budgets, AiBillLinkService links, AiAccessService access) {
        this.budgets = budgets; this.links = links; this.access = access;
    }
    @GetMapping("/budgets")
    public ApiResponse<List<AiBudgetVO>> budgets(@RequestParam String month, HttpServletRequest request) {
        return ApiResponse.success(budgets.list(access.requireCurrentUserId(request), month));
    }
    @PutMapping("/budgets")
    public ApiResponse<Void> save(@Valid @RequestBody AiBudgetRequest budget, HttpServletRequest request) {
        budgets.save(access.requireCurrentUserId(request), budget);
        return ApiResponse.success();
    }
    @PostMapping("/reminders/check")
    public ApiResponse<Void> check(HttpServletRequest request) {
        budgets.checkReminders(access.requireCurrentUserId(request));
        return ApiResponse.success();
    }
    @GetMapping("/payments/{id}/bill-link")
    public ApiResponse<AiBillLinkVO> link(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(links.get(access.requireCurrentUserId(request), id));
    }
    @GetMapping("/payments/{id}/bill-candidates")
    public ApiResponse<List<AiBillLinkVO>> candidates(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(links.candidates(access.requireCurrentUserId(request), id));
    }
    @PostMapping("/payments/{id}/bill-link")
    public ApiResponse<AiBillLinkVO> saveLink(@PathVariable Long id, @RequestBody AiBillLinkRequest link, HttpServletRequest request) {
        return ApiResponse.success(links.link(access.requireCurrentUserId(request), id, link.existingBillId()));
    }
    @DeleteMapping("/payments/{id}/bill-link")
    public ApiResponse<Void> unlink(@PathVariable Long id, HttpServletRequest request) {
        links.unlink(access.requireCurrentUserId(request), id);
        return ApiResponse.success();
    }
}
