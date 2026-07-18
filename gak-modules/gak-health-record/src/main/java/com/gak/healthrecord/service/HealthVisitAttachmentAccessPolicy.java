package com.gak.healthrecord.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 就诊病历附件只允许就诊记录所有者访问。
 */
@Component
public class HealthVisitAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    private final HealthVisitMapper healthVisitMapper;

    public HealthVisitAttachmentAccessPolicy(HealthVisitMapper healthVisitMapper) {
        this.healthVisitMapper = healthVisitMapper;
    }

    @Override
    public String businessType() { return AttachmentConstants.BUSINESS_HEALTH_VISIT; }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        HealthVisit visit = healthVisitMapper.selectById(businessId);
        return visit != null && Objects.equals(visit.getOwnerUserId(), currentUserId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) { return canView(currentUserId, businessId); }
}
