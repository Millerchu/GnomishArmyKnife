package com.gak.healthrecord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 体检报告明细条目请求。
 */
public class HealthReportDetailItemRequest {

    @NotBlank(message = "group 不能为空")
    private String group;

    private String itemCode;

    @NotBlank(message = "name 不能为空")
    @Size(max = 80, message = "name 长度不能超过 80")
    private String name;

    @NotBlank(message = "resultType 不能为空")
    private String resultType;

    @NotBlank(message = "resultText 不能为空")
    @Size(max = 1000, message = "resultText 长度不能超过 1000")
    private String resultText;

    @Size(max = 24, message = "unit 长度不能超过 24")
    private String unit;

    @Size(max = 160, message = "referenceText 长度不能超过 160")
    private String referenceText;

    private String abnormalFlag;

    @Size(max = 255, message = "sourceFileLabel 长度不能超过 255")
    private String sourceFileLabel;

    @Min(value = 1, message = "sourcePage 必须在 1 到 9999 之间")
    @Max(value = 9999, message = "sourcePage 必须在 1 到 9999 之间")
    private Integer sourcePage;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getReferenceText() {
        return referenceText;
    }

    public void setReferenceText(String referenceText) {
        this.referenceText = referenceText;
    }

    public String getAbnormalFlag() {
        return abnormalFlag;
    }

    public void setAbnormalFlag(String abnormalFlag) {
        this.abnormalFlag = abnormalFlag;
    }

    public String getSourceFileLabel() {
        return sourceFileLabel;
    }

    public void setSourceFileLabel(String sourceFileLabel) {
        this.sourceFileLabel = sourceFileLabel;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }
}
