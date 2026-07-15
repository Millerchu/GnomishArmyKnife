package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * WoW 角色单套键位方案保存请求。
 */
public class SaveWowCharacterKeybindingRequest {

    @NotBlank(message = "bindingName 不能为空")
    @Size(max = 64, message = "bindingName 长度不能超过 64")
    private String bindingName;

    private String bindingContent;

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    public String getBindingContent() {
        return bindingContent;
    }

    public void setBindingContent(String bindingContent) {
        this.bindingContent = bindingContent;
    }
}
