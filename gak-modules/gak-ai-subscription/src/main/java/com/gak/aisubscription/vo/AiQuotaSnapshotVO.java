package com.gak.aisubscription.vo;

import com.gak.aisubscription.dto.AiQuotaWindow;
import com.gak.aisubscription.dto.AiQuotaReset;
import java.time.LocalDateTime;
import java.util.List;

/** 额度历史快照，不参与费用账单。 */
public record AiQuotaSnapshotVO(Long id, Long accountId, LocalDateTime observedAt,
        List<AiQuotaWindow> windows, List<AiQuotaReset> resets) {}
