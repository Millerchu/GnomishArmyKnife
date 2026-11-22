package com.gak.instrumentpractice.vo;

/**
 * 当前用户可载入的简谱，曲谱 ID 用字符串返回以保留精度。
 */
public record InstrumentPracticeScoreVO(
        String id, String title, String notation, Integer tonic, String mode,
        String meter, Integer bpm, String instrumentId, String tuningId
) {
}
