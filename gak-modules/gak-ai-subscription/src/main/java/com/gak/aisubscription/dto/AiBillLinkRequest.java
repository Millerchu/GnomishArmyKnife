package com.gak.aisubscription.dto;
/** existingBillId 为空时显式生成账单，否则仅建立引用。 */
public record AiBillLinkRequest(Long existingBillId) {}
