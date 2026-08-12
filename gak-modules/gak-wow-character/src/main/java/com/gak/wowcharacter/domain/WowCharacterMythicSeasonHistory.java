package com.gak.wowcharacter.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WoW 角色大秘境赛季归档实体。
 */
@TableName("gak_wow_character_mythic_season_history")
public class WowCharacterMythicSeasonHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long characterId;
    private Long ownerUserId;
    private String seasonCode;
    private String seasonName;
    private BigDecimal mythicScore;
    private String dungeonSnapshotJson;
    private LocalDateTime archivedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getSeasonCode() { return seasonCode; }
    public void setSeasonCode(String seasonCode) { this.seasonCode = seasonCode; }
    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }
    public BigDecimal getMythicScore() { return mythicScore; }
    public void setMythicScore(BigDecimal mythicScore) { this.mythicScore = mythicScore; }
    public String getDungeonSnapshotJson() { return dungeonSnapshotJson; }
    public void setDungeonSnapshotJson(String dungeonSnapshotJson) { this.dungeonSnapshotJson = dungeonSnapshotJson; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
