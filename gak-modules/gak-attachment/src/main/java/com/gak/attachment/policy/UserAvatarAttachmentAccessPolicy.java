package com.gak.attachment.policy;

import com.gak.attachment.constant.AttachmentConstants;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 用户头像首期仅本人可查看和管理，避免通过附件 ID 获取其他用户资料。
 */
@Component
public class UserAvatarAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    @Override
    public String businessType() {
        return AttachmentConstants.BUSINESS_USER_AVATAR;
    }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        return Objects.equals(currentUserId, businessId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) {
        return canView(currentUserId, businessId);
    }
}
