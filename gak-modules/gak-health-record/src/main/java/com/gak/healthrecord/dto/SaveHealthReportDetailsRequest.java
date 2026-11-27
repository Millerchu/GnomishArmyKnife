package com.gak.healthrecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 体检报告明细整组替换请求。
 */
public class SaveHealthReportDetailsRequest {

    @NotNull(message = "expectedRevision 不能为空")
    @Min(value = 0, message = "expectedRevision 不能小于 0")
    private Integer expectedRevision;

    private Long linkedRecordId;

    /** 区分 JSON 中显式传入 linkedRecordId: null 与缺省该键。 */
    private boolean linkedRecordIdPresent;

    @NotNull(message = "items 不能为空")
    @Size(max = 200, message = "每份报告最多保存 200 项明细")
    @Valid
    private List<HealthReportDetailItemRequest> items;

    @AssertTrue(message = "linkedRecordId 键必传")
    public boolean isLinkedRecordIdPresent() {
        return linkedRecordIdPresent;
    }

    public Integer getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(Integer expectedRevision) {
        this.expectedRevision = expectedRevision;
    }

    public Long getLinkedRecordId() {
        return linkedRecordId;
    }

    public void setLinkedRecordId(Long linkedRecordId) {
        this.linkedRecordId = linkedRecordId;
        this.linkedRecordIdPresent = true;
    }

    public List<HealthReportDetailItemRequest> getItems() {
        return items;
    }

    public void setItems(List<HealthReportDetailItemRequest> items) {
        this.items = items;
    }
}
