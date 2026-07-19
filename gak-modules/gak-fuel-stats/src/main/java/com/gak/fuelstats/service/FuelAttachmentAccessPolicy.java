package com.gak.fuelstats.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 加油凭证只允许记录所有者访问和管理。
 */
@Component
public class FuelAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    private final FuelRecordMapper fuelRecordMapper;

    public FuelAttachmentAccessPolicy(FuelRecordMapper fuelRecordMapper) {
        this.fuelRecordMapper = fuelRecordMapper;
    }

    @Override
    public String businessType() {
        return AttachmentConstants.BUSINESS_FUEL_RECORD;
    }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        FuelRecord record = fuelRecordMapper.selectById(businessId);
        return record != null && Objects.equals(record.getOwnerUserId(), currentUserId);
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) {
        return canView(currentUserId, businessId);
    }
}
