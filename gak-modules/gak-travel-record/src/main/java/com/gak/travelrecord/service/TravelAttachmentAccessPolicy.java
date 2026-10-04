package com.gak.travelrecord.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.travelrecord.domain.TravelRecord;
import com.gak.travelrecord.mapper.TravelRecordMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 票据原图和下载均只允许行程所有者访问。 */
@Component
public class TravelAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {
    private final TravelRecordMapper mapper;
    public TravelAttachmentAccessPolicy(TravelRecordMapper mapper) { this.mapper = mapper; }
    @Override public String businessType() { return AttachmentConstants.BUSINESS_TRAVEL_RECORD; }
    @Override public boolean canView(Long user, Long id) {
        TravelRecord record = mapper.selectById(id);
        return record != null && Objects.equals(user, record.getOwnerUserId())
                && mapper.hasAppAccess(user, "APP_TRAVEL_RECORD");
    }
    @Override public boolean canManage(Long user, Long id) { return canView(user, id); }
}
