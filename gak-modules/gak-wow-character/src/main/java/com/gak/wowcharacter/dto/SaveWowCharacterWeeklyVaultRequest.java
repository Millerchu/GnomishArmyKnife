package com.gak.wowcharacter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * WoW 角色每周低保记录请求。
 */
public class SaveWowCharacterWeeklyVaultRequest {

    private Long id;

    @NotNull(message = "weekStartDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStartDate;

    @Min(value = 0, message = "raidProgressCount 不能小于 0")
    @Max(value = 99, message = "raidProgressCount 不能大于 99")
    private Integer raidProgressCount;

    @Min(value = 0, message = "mythicProgressCount 不能小于 0")
    @Max(value = 99, message = "mythicProgressCount 不能大于 99")
    private Integer mythicProgressCount;

    @Min(value = 0, message = "worldProgressCount 不能小于 0")
    @Max(value = 99, message = "worldProgressCount 不能大于 99")
    private Integer worldProgressCount;

    @Size(max = 255, message = "note 长度不能超过 255")
    private String note;

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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
