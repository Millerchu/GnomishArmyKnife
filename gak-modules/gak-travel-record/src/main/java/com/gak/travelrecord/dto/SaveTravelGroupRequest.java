package com.gak.travelrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SaveTravelGroupRequest：个人行程请求参数。 */

public class SaveTravelGroupRequest {
    @NotBlank @Size(max = 96)
    private String name;
    @Size(max = 1000)
    private String note;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
}
