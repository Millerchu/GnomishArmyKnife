package com.gak.datamigration.dto;

import com.gak.datamigration.service.AttachmentMigrationSupport.TransferItem;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.travelrecord.domain.TravelGroup;
import com.gak.travelrecord.domain.TravelRecord;
import java.util.List;

/** 行程、分组、关联账单与票据的完整迁移载荷。 */
public class TravelRecordMigrationPayload {
    private List<TravelRecord> records;
    private List<TravelGroup> groups;
    private List<PersonalBill> bills;
    private List<TransferItem> attachments;

    public List<TravelRecord> getRecords() { return records; }
    public void setRecords(List<TravelRecord> records) { this.records = records; }
    public List<TravelGroup> getGroups() { return groups; }
    public void setGroups(List<TravelGroup> groups) { this.groups = groups; }
    public List<PersonalBill> getBills() { return bills; }
    public void setBills(List<PersonalBill> bills) { this.bills = bills; }
    public List<TransferItem> getAttachments() { return attachments; }
    public void setAttachments(List<TransferItem> attachments) { this.attachments = attachments; }
}
