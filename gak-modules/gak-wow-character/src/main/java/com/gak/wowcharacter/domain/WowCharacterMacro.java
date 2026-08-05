package com.gak.wowcharacter.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * WoW 角色专用宏记录实体。
 */
@TableName("gak_wow_character_macro")
public class WowCharacterMacro {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long characterId;
    private Long ownerUserId;
    private String macroName;
    private String macroContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getMacroName() { return macroName; }
    public void setMacroName(String macroName) { this.macroName = macroName; }
    public String getMacroContent() { return macroContent; }
    public void setMacroContent(String macroContent) { this.macroContent = macroContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
