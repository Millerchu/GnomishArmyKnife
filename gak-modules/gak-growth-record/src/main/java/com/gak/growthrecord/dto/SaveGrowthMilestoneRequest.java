package com.gak.growthrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/** 保存GrowthMilestone的输入；用户与宝宝归属由接口路径和登录态确定。 */
public class SaveGrowthMilestoneRequest {
    @NotBlank(message = "里程碑标题不能为空")
    @Size(max = 128, message = "标题不能超过128字")
    private String title;

    @NotNull(message = "发生日期不能为空")
    @PastOrPresent(message = "发生日期不能晚于今天")
    private LocalDate eventDate;

    @Size(max = 1000, message = "故事不能超过1000字")
    private String note;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}


