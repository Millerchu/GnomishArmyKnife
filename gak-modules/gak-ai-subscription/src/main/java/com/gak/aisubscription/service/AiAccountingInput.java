package com.gak.aisubscription.service;

import com.gak.aisubscription.domain.AiPayment;
import com.gak.aisubscription.domain.AiSubscription;
import com.gak.aisubscription.domain.AiUsageRecord;
import com.gak.aisubscription.vo.AiAccountVO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 一次加载的统计输入，在各月份和工具间复用。 */
record AiAccountingInput(List<AiPayment> payments, List<AiSubscription> subscriptions,
        List<AiUsageRecord> usage, List<AiAccountVO> accounts) {
    AiAccountingInput forTool(String toolCode) {
        List<AiAccountVO> selected = accounts.stream().filter(account -> toolCode.equals(account.toolCode())).toList();
        Set<Long> ids = selected.stream().map(AiAccountVO::id).collect(Collectors.toSet());
        return new AiAccountingInput(
                payments.stream().filter(payment -> ids.contains(payment.getAccountId())).toList(),
                subscriptions.stream().filter(subscription -> ids.contains(subscription.getAccountId())).toList(),
                usage.stream().filter(record -> ids.contains(record.getAccountId())).toList(), selected);
    }
}
