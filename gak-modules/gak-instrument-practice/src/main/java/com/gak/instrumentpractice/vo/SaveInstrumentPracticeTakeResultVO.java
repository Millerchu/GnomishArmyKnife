package com.gak.instrumentpractice.vo;

/**
 * 保存事件录音后的结果。达到容量上限时会附带被覆盖的最旧记录标识。
 */
public class SaveInstrumentPracticeTakeResultVO {

    private InstrumentPracticeTakeVO take;
    private String overwrittenTakeId;
    private java.util.List<String> overwrittenTakeIds;

    public InstrumentPracticeTakeVO getTake() {
        return take;
    }

    public void setTake(InstrumentPracticeTakeVO take) {
        this.take = take;
    }

    public java.util.List<String> getOverwrittenTakeIds() {
        return overwrittenTakeIds;
    }

    public void setOverwrittenTakeIds(java.util.List<String> overwrittenTakeIds) {
        this.overwrittenTakeIds = overwrittenTakeIds;
    }

    public String getOverwrittenTakeId() {
        return overwrittenTakeId;
    }

    public void setOverwrittenTakeId(String overwrittenTakeId) {
        this.overwrittenTakeId = overwrittenTakeId;
    }
}
