package com.gak.healthrecord.service;

import com.gak.healthrecord.mapper.DentalCleaningMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 定期将到期提醒发到用户的站内消息。 */
@Component
public class DentalReminderJob {
    private static final Logger LOG = LoggerFactory.getLogger(DentalReminderJob.class);
    private final DentalCleaningMapper mapper;
    private final DentalCleaningService service;

    public DentalReminderJob(DentalCleaningMapper mapper, DentalCleaningService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Shanghai")
    public void sendDueReminders() {
        LocalDate today = DentalCleaningService.today();
        for (Long owner : mapper.dueOwners(today)) {
            try {
                service.sendIfDue(owner, today);
            } catch (RuntimeException exception) {
                LOG.warn("洁牙提醒发送失败，下次调度重试，用户 ID {}", owner, exception);
            }
        }
    }
}
