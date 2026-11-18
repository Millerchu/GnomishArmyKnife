package com.gak.aisubscription.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** API Key 仅用于输入；禁用默认 record toString，防止异常日志泄漏。 */
public final class SaveAiSyncConnectionRequest {
    @NotBlank @Size(max = 512) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;
    @NotBlank @Size(max = 64)
    private String zoneId = "Asia/Shanghai";
    @Size(max = 64)
    private String teamId;
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
}
