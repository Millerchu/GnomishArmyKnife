package com.gak.instrumentpractice.vo;

import com.gak.instrumentpractice.dto.PerformanceEventRequest;
import java.util.List;

/**
 * 返回给客户端的随身乐器事件录音。
 */
public class InstrumentPracticeTakeVO {

    private String id;
    private String instrumentId;
    private String tuningId;
    private Integer bpm;
    private String meter;
    private Long durationMs;
    private List<PerformanceEventRequest> events;
    private Long createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
