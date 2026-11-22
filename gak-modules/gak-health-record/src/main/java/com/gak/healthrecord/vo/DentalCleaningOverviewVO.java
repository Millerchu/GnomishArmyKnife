package com.gak.healthrecord.vo;

import java.util.List;

/** 洁牙区块的历史与提醒。 */
public record DentalCleaningOverviewVO(List<DentalCleaningVO> records, DentalReminderVO reminder) {
}
