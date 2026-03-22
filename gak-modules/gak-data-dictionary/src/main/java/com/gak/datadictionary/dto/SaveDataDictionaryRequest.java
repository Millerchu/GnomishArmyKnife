package com.gak.datadictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 数据字典新增/编辑请求。
 */
public class SaveDataDictionaryRequest {

    @NotBlank(message = "dictCode 不能为空")
    @Size(max = 64, message = "dictCode 长度不能超过 64")
    private String dictCode;

    @NotBlank(message = "dictName 不能为空")
    @Size(max = 64, message = "dictName 长度不能超过 64")
    private String dictName;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    private Boolean enabled;

    private List<@Size(max = 64, message = "referenceApps 单项长度不能超过 64") String> referenceApps;

    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
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

    public List<String> getReferenceApps() {
        return referenceApps;
    }

    public void setReferenceApps(List<String> referenceApps) {
        this.referenceApps = referenceApps;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
