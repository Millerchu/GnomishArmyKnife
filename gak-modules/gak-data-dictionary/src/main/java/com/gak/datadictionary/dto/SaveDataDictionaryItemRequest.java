package com.gak.datadictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 数据字典项新增/编辑请求。
 */
public class SaveDataDictionaryItemRequest {

    @NotBlank(message = "itemCode 不能为空")
    @Size(max = 64, message = "itemCode 长度不能超过 64")
    private String itemCode;

    @NotBlank(message = "itemLabel 不能为空")
    @Size(max = 64, message = "itemLabel 长度不能超过 64")
    private String itemLabel;

    @NotBlank(message = "itemValue 不能为空")
    @Size(max = 64, message = "itemValue 长度不能超过 64")
    private String itemValue;

    private Integer sort;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    private Boolean enabled;

    private Boolean isDefault;

    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public void setItemLabel(String itemLabel) {
        this.itemLabel = itemLabel;
    }

    public String getItemValue() {
        return itemValue;
    }

    public void setItemValue(String itemValue) {
        this.itemValue = itemValue;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
