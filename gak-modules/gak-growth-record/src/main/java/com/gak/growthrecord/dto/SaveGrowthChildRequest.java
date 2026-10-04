package com.gak.growthrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import com.gak.growthrecord.enums.ChildGender;

/** 保存GrowthChild的输入；用户与宝宝归属由接口路径和登录态确定。 */
public class SaveGrowthChildRequest {
    @NotBlank(message = "宝宝昵称不能为空")
    @Size(max = 64, message = "宝宝昵称不能超过64字")
    private String name;

    @NotNull(message = "出生日期不能为空")
    @PastOrPresent(message = "出生日期不能晚于今天")
    private LocalDate birthday;

    @NotNull(message = "请选择性别或暂不填写")
    private ChildGender gender;

    @Size(max = 1000, message = "备注不能超过1000字")
    private String note;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    public ChildGender getGender() { return gender; }
    public void setGender(ChildGender gender) { this.gender = gender; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}


