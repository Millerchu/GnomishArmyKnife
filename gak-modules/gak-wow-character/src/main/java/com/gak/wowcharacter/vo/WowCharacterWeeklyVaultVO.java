package com.gak.wowcharacter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import com.gak.attachment.vo.AttachmentVO;

/**
 * WoW 角色每周低保记录视图。
 */
public class WowCharacterWeeklyVaultVO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStartDate;

    private Integer raidProgressCount;
    private Integer mythicProgressCount;
    private Integer worldProgressCount;
    private Integer raidUnlockedCount;
    private Integer mythicUnlockedCount;
    private Integer worldUnlockedCount;
    private String note;
    private List<AttachmentVO> attachments;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getRaidUnlockedCount() {
        return raidUnlockedCount;
    }

    public void setRaidUnlockedCount(Integer raidUnlockedCount) {
        this.raidUnlockedCount = raidUnlockedCount;
    }

    public Integer getMythicUnlockedCount() {
        return mythicUnlockedCount;
    }

    public void setMythicUnlockedCount(Integer mythicUnlockedCount) {
        this.mythicUnlockedCount = mythicUnlockedCount;
    }

    public Integer getWorldUnlockedCount() {
        return worldUnlockedCount;
    }

    public void setWorldUnlockedCount(Integer worldUnlockedCount) {
        this.worldUnlockedCount = worldUnlockedCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<AttachmentVO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentVO> attachments) {
        this.attachments = attachments;
    }
}
