package com.gak.aisubscription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/** 一次采集的全部窗口和重置明细。 */
public record SaveAiQuotaSnapshotRequest(@NotNull Long accountId, @NotNull LocalDateTime observedAt,
        @NotEmpty @Size(max = 12) List<@NotNull @Valid AiQuotaWindow> windows,
        @Size(max = 30) List<@NotNull @Valid AiQuotaReset> resets) {}
