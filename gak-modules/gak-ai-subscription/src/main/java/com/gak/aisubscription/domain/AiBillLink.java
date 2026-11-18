package com.gak.aisubscription.domain;
/** 仅保留支付与账单引用，解绑不会删除原账单。 */
public record AiBillLink(Long ownerUserId, Long paymentId, Long billId, boolean generated) {}
