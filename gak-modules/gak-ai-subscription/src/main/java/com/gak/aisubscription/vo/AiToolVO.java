package com.gak.aisubscription.vo;
/** 官方能力未经核验时只开放手动记录。 */
public record AiToolVO(String code, String name, boolean syncSupported, String syncNote) {}
