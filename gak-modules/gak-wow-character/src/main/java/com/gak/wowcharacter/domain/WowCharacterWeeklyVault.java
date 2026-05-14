package com.gak.wowcharacter.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * WoW 角色每周低保记录实体。
 */
@TableName("gak_wow_character_weekly_vault")
public class WowCharacterWeeklyVault {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long characterId;
    private Long ownerUserId;
    private LocalDate weekStartDate;
    private Integer raidProgressCount;
    private Integer mythicProgressCount;
    private Integer worldProgressCount;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public Integer getRaidProgressCount() {
        return raidProgressCount;
    }

    public void setRaidProgressCount(Integer raidProgressCount) {
        this.raidProgressCount = raidProgressCount;
    }

    public Integer getMythicProgressCount() {
        return mythicProgressCount;
    }

    public void setMythicProgressCount(Integer mythicProgressCount) {
        this.mythicProgressCount = mythicProgressCount;
    }

    public Integer getWorldProgressCount() {
        return worldProgressCount;
    }

    public void setWorldProgressCount(Integer worldProgressCount) {
        this.worldProgressCount = worldProgressCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
