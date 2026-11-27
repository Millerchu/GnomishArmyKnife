package com.gak.attachment.policy;

import com.gak.framework.security.AppPermissionCheck;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 附件业务权限策略注册表。
 */
@Component
public class AttachmentPolicyRegistry {

    private final Map<String, AttachmentBusinessAccessPolicy> policies = new HashMap<>();

    private static final Map<String, String> REQUIRED_APPS = Map.of(
            "HEALTH_REPORT", "APP_HEALTH_RECORD",
            "HEALTH_VISIT", "APP_HEALTH_RECORD",
            "KNOWLEDGE_ENTRY", "APP_KNOWLEDGE_BASE",
            "FUEL_RECORD", "APP_FUEL_STATS",
            "WOW_WEEKLY_VAULT", "APP_WOW_CHARACTER",
            "TRAVEL_RECORD", "APP_TRAVEL_RECORD");

    private final AppPermissionCheck permissions;

    public AttachmentPolicyRegistry(List<AttachmentBusinessAccessPolicy> policyList, AppPermissionCheck permissions) {
        this.permissions = permissions;
        for (AttachmentBusinessAccessPolicy policy : policyList) {
            policies.put(policy.businessType(), policy);
        }
    }

    public boolean canView(String businessType, Long currentUserId, Long businessId) {
        AttachmentBusinessAccessPolicy policy = policies.get(businessType);
        return policy != null && hasAppAccess(businessType, currentUserId) && policy.canView(currentUserId, businessId);
    }

    public boolean canManage(String businessType, Long currentUserId, Long businessId) {
        AttachmentBusinessAccessPolicy policy = policies.get(businessType);
        return policy != null && hasAppAccess(businessType, currentUserId) && policy.canManage(currentUserId, businessId);
    }

    private boolean hasAppAccess(String businessType, Long userId) {
        String appCode = REQUIRED_APPS.get(businessType);
        return appCode == null || permissions.isAllowed(userId, appCode);
    }
}
