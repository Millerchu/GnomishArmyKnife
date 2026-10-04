package com.gak.growthrecord.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.gak.growthrecord.enums.ChildGender;

/** GrowthChild响应，不公开用户归属字段。 */
public class GrowthChildVO {
    private Long id;
    private String name;
    private LocalDate birthday;
    private ChildGender gender;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    public ChildGender getGender() { return gender; }
    public void setGender(ChildGender gender) { this.gender = gender; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}


