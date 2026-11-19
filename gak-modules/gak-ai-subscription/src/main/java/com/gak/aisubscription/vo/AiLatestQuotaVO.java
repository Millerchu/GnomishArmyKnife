package com.gak.aisubscription.vo;

import com.gak.aisubscription.dto.AiQuotaWindow;
import java.time.LocalDateTime;
import java.util.List;

/** 工作台所需的活跃账号与最新额度窗口。 */
public record AiLatestQuotaVO(Long accountId, String alias, String toolCode, LocalDateTime observedAt,
        List<AiQuotaWindow> windows) {}
