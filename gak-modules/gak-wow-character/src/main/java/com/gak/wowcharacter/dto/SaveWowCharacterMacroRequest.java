package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * WoW 角色专用宏保存请求。
 */
public class SaveWowCharacterMacroRequest {

    @NotBlank(message = "macroName 不能为空")
    @Size(max = 64, message = "macroName 长度不能超过 64")
    private String macroName;

    @NotBlank(message = "macroContent 不能为空")
    private String macroContent;

    public String getMacroName() { return macroName; }
    public void setMacroName(String macroName) { this.macroName = macroName; }
    public String getMacroContent() { return macroContent; }
    public void setMacroContent(String macroContent) { this.macroContent = macroContent; }
}
