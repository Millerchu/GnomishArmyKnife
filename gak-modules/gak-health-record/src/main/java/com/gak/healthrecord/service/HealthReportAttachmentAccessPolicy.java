package com.gak.healthrecord.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.mapper.HealthReportMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 健康报告附件只允许报告所有者访问。
 */
@Component
public class HealthReportAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    private final HealthReportMapper healthReportMapper;

    public HealthReportAttachmentAccessPolicy(HealthReportMapper healthReportMapper) {
        this.healthReportMapper = healthReportMapper;
    }

    @Override
    public String businessType() { return AttachmentConstants.BUSINESS_HEALTH_REPORT; }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        HealthReport report = healthReportMapper.selectById(businessId);
        return report != null && Objects.equals(report.getOwnerUserId(), currentUserId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) { return canView(currentUserId, businessId); }
}
