package com.gak.worklog.service;

import java.math.BigDecimal;

/** 规范化后的工作内容及投入，仅在工作日志服务内使用。 */
record NormalizedWorkItem(String content, String status, String zentaoNo, BigDecimal personDay) {
}
