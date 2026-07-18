package com.gak.attachment.policy;

/**
 * 业务模块实现自己的附件可见性和管理权限，附件模块只负责统一调度。
 */
public interface AttachmentBusinessAccessPolicy {

    String businessType();

    boolean canView(Long currentUserId, Long businessId);

    boolean canManage(Long currentUserId, Long businessId);
}
