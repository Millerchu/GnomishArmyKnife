package com.gak.attachment.policy;

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

    public AttachmentPolicyRegistry(List<AttachmentBusinessAccessPolicy> policyList) {
        for (AttachmentBusinessAccessPolicy policy : policyList) {
            policies.put(policy.businessType(), policy);
        }
    }

    public boolean canView(String businessType, Long currentUserId, Long businessId) {
        AttachmentBusinessAccessPolicy policy = policies.get(businessType);
        return policy != null && policy.canView(currentUserId, businessId);
    }

    public boolean canManage(String businessType, Long currentUserId, Long businessId) {
        AttachmentBusinessAccessPolicy policy = policies.get(businessType);
        return policy != null && policy.canManage(currentUserId, businessId);
    }
}
