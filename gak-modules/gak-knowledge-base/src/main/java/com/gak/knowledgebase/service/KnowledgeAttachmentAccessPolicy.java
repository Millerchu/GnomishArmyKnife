package com.gak.knowledgebase.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import com.gak.knowledgebase.enums.KnowledgeEntryStatus;
import com.gak.knowledgebase.mapper.KnowledgeEntryMapper;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 经验图片权限跟随审核状态动态变化，避免发布时批量修改附件权限。
 */
@Component
public class KnowledgeAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final UserMapper userMapper;

    public KnowledgeAttachmentAccessPolicy(KnowledgeEntryMapper knowledgeEntryMapper, UserMapper userMapper) {
        this.knowledgeEntryMapper = knowledgeEntryMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String businessType() {
        return AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY;
    }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        KnowledgeEntry entry = knowledgeEntryMapper.selectById(businessId);
        if (entry == null) return false;
        if (KnowledgeEntryStatus.PUBLISHED.name().equals(entry.getStatus())) return true;
        return Objects.equals(entry.getOwnerUserId(), currentUserId) || isAdmin(currentUserId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) {
        KnowledgeEntry entry = knowledgeEntryMapper.selectById(businessId);
        if (entry == null) return false;
        if (isAdmin(currentUserId)) return true;
        return Objects.equals(entry.getOwnerUserId(), currentUserId)
                && !KnowledgeEntryStatus.PUBLISHED.name().equals(entry.getStatus());
    }

    private boolean isAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(user.getRoleCode());
    }
}
