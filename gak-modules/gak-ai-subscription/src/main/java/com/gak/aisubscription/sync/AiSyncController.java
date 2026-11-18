package com.gak.aisubscription.sync;

import com.gak.aisubscription.service.AiAccessService;
import com.gak.framework.response.ApiResponse;
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

/** 同步配置与任务查询；所有入口复用智订功能授权。 */
@RestController
@RequestMapping("/ai-subscriptions")
public class AiSyncController {
    private final AiAccessService access;
    private final AiSyncService service;
    public AiSyncController(AiAccessService access, AiSyncService service) { this.access = access; this.service = service; }
    @GetMapping("/accounts/{id}/sync-connection")
    public ApiResponse<AiSyncConnectionVO> connection(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.connection(access.requireCurrentUserId(request), id));
    }
    @PutMapping("/accounts/{id}/sync-connection")
    public ApiResponse<AiSyncConnectionVO> save(@PathVariable Long id, @Valid @RequestBody SaveAiSyncConnectionRequest body,
                                               HttpServletRequest request) {
        return ApiResponse.success(service.save(access.requireCurrentUserId(request), id, body));
    }
    @DeleteMapping("/accounts/{id}/sync-connection")
    public ApiResponse<Void> revoke(@PathVariable Long id, HttpServletRequest request) {
        service.revoke(access.requireCurrentUserId(request), id); return ApiResponse.success();
    }
    @PostMapping("/sync/daily-check")
    public ApiResponse<List<AiSyncJobVO>> daily(HttpServletRequest request) {
        return ApiResponse.success(service.dailyCheck(access.requireCurrentUserId(request)));
    }
    @PostMapping("/accounts/{id}/sync")
    public ApiResponse<AiSyncJobVO> manual(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.manual(access.requireCurrentUserId(request), id));
    }
    @GetMapping("/sync/jobs/{id}")
    public ApiResponse<AiSyncJobVO> job(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.job(access.requireCurrentUserId(request), id));
    }
}
