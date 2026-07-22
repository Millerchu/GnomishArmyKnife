package com.gak.wowcharacter.vo;

/**
 * WoW 角色键位方案视图。
 */
public class WowCharacterKeybindingVO {

    private String bindingName;
    private Boolean hasKeybinding;
    private String bindingContent;

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    public Boolean getHasKeybinding() {
        return hasKeybinding;
    }

    public void setHasKeybinding(Boolean hasKeybinding) {
        this.hasKeybinding = hasKeybinding;
    }

    public String getBindingContent() {
        return bindingContent;
    }

    public void setBindingContent(String bindingContent) {
        this.bindingContent = bindingContent;
    }
}
