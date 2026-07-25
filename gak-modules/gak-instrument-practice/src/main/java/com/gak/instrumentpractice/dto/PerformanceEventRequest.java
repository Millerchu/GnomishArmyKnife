package com.gak.instrumentpractice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 一条可回放的乐器演奏事件。
 */
public class PerformanceEventRequest {

    @NotNull(message = "事件时间不能为空")
    @Min(value = 0, message = "事件时间不能小于 0")
    @Max(value = 600000, message = "事件时间不能超过 10 分钟")
    private Long at;

    @NotBlank(message = "事件类型不能为空")
    @Size(max = 12, message = "事件类型长度不能超过 12")
    private String type;

    @NotBlank(message = "事件乐器不能为空")
    @Size(max = 24, message = "事件乐器长度不能超过 24")
    private String instrumentId;

    @Size(max = 64, message = "琴弦标识长度不能超过 64")
    private String stringId;

    @Min(value = 0, message = "MIDI 音高不能小于 0")
    @Max(value = 127, message = "MIDI 音高不能大于 127")
    private Integer midi;

    @DecimalMin(value = "0.0", message = "力度不能小于 0")
    @DecimalMax(value = "1.0", message = "力度不能大于 1")
    private Double velocity;

    @Min(value = -2400, message = "弯音不能低于 -2400 音分")
    @Max(value = 2400, message = "弯音不能高于 2400 音分")
    private Integer bendCents;

    private Boolean damped;

    public Long getAt() {
        return at;
    }

    public void setAt(Long at) {
        this.at = at;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getStringId() {
        return stringId;
    }

    public void setStringId(String stringId) {
        this.stringId = stringId;
    }

    public Integer getMidi() {
        return midi;
    }

    public void setMidi(Integer midi) {
        this.midi = midi;
    }

    public Double getVelocity() {
        return velocity;
    }

    public void setVelocity(Double velocity) {
        this.velocity = velocity;
    }

    public Integer getBendCents() {
        return bendCents;
    }

    public void setBendCents(Integer bendCents) {
        this.bendCents = bendCents;
    }

    public Boolean getDamped() {
        return damped;
    }

    public void setDamped(Boolean damped) {
        this.damped = damped;
    }
}
