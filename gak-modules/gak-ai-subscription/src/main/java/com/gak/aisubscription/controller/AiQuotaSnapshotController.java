package com.gak.aisubscription.controller;

import com.gak.aisubscription.dto.SaveAiQuotaSnapshotRequest;
import com.gak.aisubscription.service.AiAccessService;
import com.gak.aisubscription.service.AiQuotaSnapshotService;
import com.gak.aisubscription.vo.AiQuotaSnapshotVO;
import com.gak.framework.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 账号的套餐额度快照接口。 */
@RestController
@RequestMapping("/ai-subscriptions/quota-snapshots")
public class AiQuotaSnapshotController {
    private final AiAccessService access;
    private final AiQuotaSnapshotService snapshots;

    public AiQuotaSnapshotController(AiAccessService access, AiQuotaSnapshotService snapshots) {
        this.access = access;
        this.snapshots = snapshots;
    }

    @GetMapping
    public ApiResponse<List<AiQuotaSnapshotVO>> list(@RequestParam Long accountId, HttpServletRequest request) {
        return ApiResponse.success(snapshots.list(access.requireCurrentUserId(request), accountId));
    }

    @PostMapping
    public ApiResponse<AiQuotaSnapshotVO> save(@Valid @RequestBody SaveAiQuotaSnapshotRequest snapshot,
            HttpServletRequest request) {
        return ApiResponse.success(snapshots.save(access.requireCurrentUserId(request), snapshot));
    }
}
