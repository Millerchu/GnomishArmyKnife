package com.gak.wowcharacter.vo;

/**
 * WoW 角色专精键位视图。
 */
public class WowCharacterKeybindingVO {

    private String specName;
    private String specNameLabel;
    private Boolean hasKeybinding;
    private String bindingContent;

    public String getSpecName() {
        return specName;
    }

    public void setSpecName(String specName) {
        this.specName = specName;
    }

    public String getSpecNameLabel() {
        return specNameLabel;
    }

    public void setSpecNameLabel(String specNameLabel) {
        this.specNameLabel = specNameLabel;
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
