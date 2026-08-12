package com.gak.wowcharacter.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.wowcharacter.domain.WowCharacterWeeklyVault;
import com.gak.wowcharacter.mapper.WowCharacterWeeklyVaultMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 低保附件仅允许记录所有者访问和管理。
 */
@Component
public class WowWeeklyVaultAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {
    private final WowCharacterWeeklyVaultMapper weeklyVaultMapper;

    public WowWeeklyVaultAttachmentAccessPolicy(WowCharacterWeeklyVaultMapper weeklyVaultMapper) {
        this.weeklyVaultMapper = weeklyVaultMapper;
    }

    @Override
    public String businessType() { return AttachmentConstants.BUSINESS_WOW_WEEKLY_VAULT; }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        WowCharacterWeeklyVault vault = weeklyVaultMapper.selectById(businessId);
        return vault != null && Objects.equals(vault.getOwnerUserId(), currentUserId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) { return canView(currentUserId, businessId); }
}
