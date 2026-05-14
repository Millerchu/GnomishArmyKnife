package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * WoW 角色单副本赛季记录请求。
 */
public class SaveWowCharacterMythicRunRequest {

    @NotBlank(message = "dungeonName 不能为空")
    @Size(max = 32, message = "dungeonName 长度不能超过 32")
    private String dungeonName;

    @Min(value = 0, message = "score 不能小于 0")
    private BigDecimal score;

    public String getDungeonName() {
        return dungeonName;
    }

    public void setDungeonName(String dungeonName) {
        this.dungeonName = dungeonName;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }
}
