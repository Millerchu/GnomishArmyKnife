package com.gak.attachment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启附件延迟清理任务。
 */
@Configuration
@EnableScheduling
public class AttachmentSchedulingConfig {
}
