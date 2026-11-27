package com.gak.healthrecord.vo;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * 健康报告兼容附件下载资源对象。
 */
public record HealthReportFileResource(Resource resource, MediaType mediaType, String fileName) {
}
