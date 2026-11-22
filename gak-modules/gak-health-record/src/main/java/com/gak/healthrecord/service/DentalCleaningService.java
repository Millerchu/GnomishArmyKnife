package com.gak.healthrecord.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gak.framework.message.MessageAudience;
import com.gak.framework.message.MessageCategory;
import com.gak.framework.message.MessagePriority;
import com.gak.framework.message.MessagePublisher;
import com.gak.framework.message.MessageTarget;
import com.gak.framework.message.PublishMessageCommand;
import com.gak.healthrecord.domain.DentalCleaning;
import com.gak.healthrecord.domain.DentalReminder;
import com.gak.healthrecord.dto.SaveDentalCleaningRequest;
import com.gak.healthrecord.dto.SaveDentalReminderRequest;
import com.gak.healthrecord.mapper.DentalCleaningMapper;
import com.gak.healthrecord.vo.DentalCleaningOverviewVO;
import com.gak.healthrecord.vo.DentalCleaningVO;
import com.gak.healthrecord.vo.DentalReminderVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** 按用户维护洁牙记录，并以最近一次洁牙为提醒周期的基准。 */
@Service
public class DentalCleaningService {
    private static final int DEFAULT_INTERVAL_MONTHS = 12;
    private static final int DEFAULT_LEAD_DAYS = 7;
    private static final int SHORTEST_MONTH_DAYS = 28;
    private static final ZoneId REMINDER_ZONE = ZoneId.of("Asia/Shanghai");

    private final DentalCleaningMapper mapper;
    private final MessagePublisher messages;

    public DentalCleaningService(DentalCleaningMapper mapper, MessagePublisher messages) {
        this.mapper = mapper;
        this.messages = messages;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DentalCleaningOverviewVO overview(Long owner) {
        requireAccess(owner);
        DentalReminder reminder = mapper.reminder(owner);
        return new DentalCleaningOverviewVO(mapper.list(owner).stream().map(this::toRecordVO).toList(),
                toReminderVO(reminder == null ? defaults(owner) : reminder));
    }

    @Transactional
    public DentalCleaningVO saveRecord(Long owner, Long id, SaveDentalCleaningRequest request) {
        requireAccess(owner);
        LocalDate today = today();
        if (request.cleaningDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "洁牙日期不能晚于今天");
        }
        DentalReminder reminder = lockOrCreate(owner);
        LocalDate previousLatest = mapper.latestDate(owner);
        BigDecimal amount = request.amount() == null ? null : request.amount().setScale(2, RoundingMode.UNNECESSARY);
        DentalCleaning record = new DentalCleaning(id == null ? IdWorker.getId() : id, owner,
                request.cleaningDate(), request.hospitalName().strip(), amount, request.note());
        if (id == null) {
            mapper.insert(record);
        } else {
            requireExisting(mapper.update(record));
        }
        refreshAfterRecordChange(reminder, previousLatest, today);
        return toRecordVO(record);
    }

    @Transactional
    public void deleteRecord(Long owner, Long id) {
        requireAccess(owner);
        DentalReminder reminder = lockOrCreate(owner);
        LocalDate previousLatest = mapper.latestDate(owner);
        requireExisting(mapper.delete(owner, id));
        refreshAfterRecordChange(reminder, previousLatest, today());
    }

    @Transactional
    public DentalReminderVO saveReminder(Long owner, SaveDentalReminderRequest request) {
        requireAccess(owner);
        int interval = request.intervalMonths();
        int lead = request.leadDays();
        if (lead >= interval * SHORTEST_MONTH_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提前天数必须短于提醒周期");
        }
        DentalReminder current = lockOrCreate(owner);
        LocalDate dueDate = current.nextDueDate();
        if (interval != current.intervalMonths()) {
            dueDate = firstDueOnOrAfter(mapper.latestDate(owner), interval, today());
        } else if (request.enabled() && !current.enabled() && dueDate != null && dueDate.isBefore(today())) {
            dueDate = firstDueOnOrAfter(mapper.latestDate(owner), interval, today());
        }
        DentalReminder updated = new DentalReminder(owner, request.enabled(), interval, lead, dueDate);
        mapper.saveReminder(updated);
        return toReminderVO(updated);
    }

    @Transactional
    public void sendIfDue(Long owner, LocalDate today) {
        // 与记录和设置保存使用同一行锁，发送与推进提醒日期一起提交。
        DentalReminder reminder = mapper.lockReminder(owner);
        if (reminder == null || !reminder.enabled() || reminder.nextDueDate() == null
                || reminder.nextDueDate().minusDays(reminder.leadDays()).isAfter(today) || !mapper.hasAccess(owner)) {
            return;
        }
        LocalDate latest = mapper.latestDate(owner);
        if (latest == null) {
            saveDueDate(reminder, null);
            return;
        }
        LocalDate dueDate = reminder.nextDueDate();
        LocalDate upcoming = firstDueOnOrAfter(latest, reminder.intervalMonths(), today);
        // 停机跨过旧周期时，优先提醒已进入提前通知窗口的新周期。
        if (!upcoming.minusDays(reminder.leadDays()).isAfter(today)) {
            dueDate = upcoming;
        }
        messages.publish(new PublishMessageCommand("HEALTH_DENTAL_CLEANING", owner + ":" + dueDate, null,
                MessageAudience.USERS, List.of(owner), MessageCategory.BUSINESS, MessagePriority.NORMAL,
                "洁牙提醒", "本轮洁牙计划日期为 " + dueDate + "，可前往健康应用查看记录和提醒设置。", MessageTarget.HOME));
        LocalDate next = firstDueOnOrAfter(latest, reminder.intervalMonths(), today.plusDays(reminder.leadDays() + 1L));
        saveDueDate(reminder, next);
    }

    static LocalDate today() {
        return LocalDate.now(REMINDER_ZONE);
    }

    static LocalDate firstDueOnOrAfter(LocalDate latest, int interval, LocalDate earliest) {
        if (latest == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(latest), YearMonth.from(earliest));
        long cycle = Math.max(1, months / interval);
        LocalDate due = latest.plusMonths(cycle * interval);
        return due.isBefore(earliest) ? latest.plusMonths((cycle + 1) * interval) : due;
    }

    private DentalReminder lockOrCreate(Long owner) {
        mapper.ensureReminder(defaults(owner));
        return mapper.lockReminder(owner);
    }

    private DentalReminder defaults(Long owner) {
        return new DentalReminder(owner, true, DEFAULT_INTERVAL_MONTHS, DEFAULT_LEAD_DAYS, null);
    }

    private void refreshAfterRecordChange(DentalReminder reminder, LocalDate previousLatest, LocalDate today) {
        LocalDate latest = mapper.latestDate(reminder.ownerUserId());
        if (!Objects.equals(previousLatest, latest)) {
            saveDueDate(reminder, firstDueOnOrAfter(latest, reminder.intervalMonths(), today));
        }
    }

    private void saveDueDate(DentalReminder reminder, LocalDate dueDate) {
        mapper.saveReminder(new DentalReminder(reminder.ownerUserId(), reminder.enabled(), reminder.intervalMonths(),
                reminder.leadDays(), dueDate));
    }

    private void requireAccess(Long owner) {
        if (!mapper.hasAccess(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未获得健康应用权限");
        }
    }

    private void requireExisting(int modified) {
        if (modified == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "洁牙记录不存在");
        }
    }

    private DentalCleaningVO toRecordVO(DentalCleaning record) {
        return new DentalCleaningVO(record.id().toString(), record.cleaningDate(), record.hospitalName(),
                record.amount(), record.note());
    }

    private DentalReminderVO toReminderVO(DentalReminder reminder) {
        LocalDate dueDate = reminder.enabled() ? reminder.nextDueDate() : null;
        LocalDate sendDate = dueDate == null ? null : dueDate.minusDays(reminder.leadDays());
        if (sendDate != null && sendDate.isBefore(today())) {
            sendDate = today();
        }
        return new DentalReminderVO(reminder.enabled(), reminder.intervalMonths(), reminder.leadDays(), dueDate, sendDate);
    }
}
