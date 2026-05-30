package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * WoW 角色单专精键位保存请求。
 */
public class SaveWowCharacterKeybindingRequest {

    @NotBlank(message = "specName 不能为空")
    @Size(max = 24, message = "specName 长度不能超过 24")
    private String specName;

    private String bindingContent;

    public String getSpecName() {
        return specName;
    }

    public void setSpecName(String specName) {
        this.specName = specName;
    }

    public String getBindingContent() {
        return bindingContent;
    }

    public void setBindingContent(String bindingContent) {
        this.bindingContent = bindingContent;
    }
}
