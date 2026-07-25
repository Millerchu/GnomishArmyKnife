package com.gak.instrumentpractice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 保存随身乐器事件录音的请求。
 */
public class SaveInstrumentPracticeTakeRequest {

    @NotBlank(message = "乐器不能为空")
    @Size(max = 24, message = "乐器长度不能超过 24")
    private String instrumentId;

    @NotBlank(message = "调弦不能为空")
    @Size(max = 64, message = "调弦长度不能超过 64")
    private String tuningId;

    @NotNull(message = "节拍速度不能为空")
    @Min(value = 40, message = "节拍速度不能低于 40")
    @Max(value = 220, message = "节拍速度不能高于 220")
    private Integer bpm;

    @NotBlank(message = "拍号不能为空")
    @Size(max = 8, message = "拍号长度不能超过 8")
    private String meter;

    @NotNull(message = "录制时长不能为空")
    @Min(value = 0, message = "录制时长不能小于 0")
    @Max(value = 600000, message = "录制时长不能超过 10 分钟")
    private Long durationMs;

    @NotNull(message = "演奏事件不能为空")
    @Size(max = 20000, message = "演奏事件数量不能超过 20000")
    @Valid
    private List<PerformanceEventRequest> events;

    public String getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getTuningId() {
        return tuningId;
    }

    public void setTuningId(String tuningId) {
        this.tuningId = tuningId;
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }

    public String getMeter() {
        return meter;
    }

    public void setMeter(String meter) {
        this.meter = meter;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public List<PerformanceEventRequest> getEvents() {
        return events;
    }

    public void setEvents(List<PerformanceEventRequest> events) {
        this.events = events;
    }
}
