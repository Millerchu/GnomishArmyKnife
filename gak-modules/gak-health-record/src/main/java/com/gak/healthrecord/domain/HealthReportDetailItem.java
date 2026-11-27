package com.gak.healthrecord.domain;

/**
 * 持久化在 JSONB 中的体检报告明细条目（仅保存可写字段，numericValue 读取时派生）。
 */
public class HealthReportDetailItem {

    private String group;
    private String itemCode;
    private String name;
    private String resultType;
    private String resultText;
    private String unit;
    private String referenceText;
    private String abnormalFlag;
    private String sourceFileLabel;
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
