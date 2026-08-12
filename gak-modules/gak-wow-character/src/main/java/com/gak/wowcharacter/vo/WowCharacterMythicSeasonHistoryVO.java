package com.gak.wowcharacter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WoW 角色大秘境赛季归档视图。
 */
public class WowCharacterMythicSeasonHistoryVO {
    private Long id;
    private String seasonCode;
    private String seasonName;
    private BigDecimal mythicScore;
    private List<WowCharacterMythicRunVO> mythicRuns;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archivedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSeasonCode() { return seasonCode; }
    public void setSeasonCode(String seasonCode) { this.seasonCode = seasonCode; }
    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }
    public BigDecimal getMythicScore() { return mythicScore; }
    public void setMythicScore(BigDecimal mythicScore) { this.mythicScore = mythicScore; }
    public List<WowCharacterMythicRunVO> getMythicRuns() { return mythicRuns; }
    public void setMythicRuns(List<WowCharacterMythicRunVO> mythicRuns) { this.mythicRuns = mythicRuns; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
