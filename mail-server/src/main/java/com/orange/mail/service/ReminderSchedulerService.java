package com.orange.mail.service;

import com.orange.mail.dto.ReminderScheduleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ThreadPoolTaskScheduler taskScheduler;
    private final EmailSenderService emailSenderService;

    /**
     * Bộ ánh xạ slotId -> ScheduledFuture để đảm bảo tính Idempotent (hủy job cũ
     * khi có job mới).
     */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    public ReminderSchedulerService(ThreadPoolTaskScheduler taskScheduler, EmailSenderService emailSenderService) {
        this.taskScheduler = taskScheduler;
        this.emailSenderService = emailSenderService;
    }

    public boolean scheduleReminder(ReminderScheduleRequest req) {
        Long slotId = req.getSlotId();

        // 1. Nếu đã có job cũ cho slot này -> Hủy job cũ trước (Cơ chế Upsert Job)
        cancelReminder(slotId);

        try {
            LocalDateTime ldt = LocalDateTime.parse(req.getReminderTime(), TIME_FMT);
            Instant triggerInstant = ldt.atZone(ZoneId.systemDefault()).toInstant();

            Instant now = Instant.now();
            if (triggerInstant.isBefore(now)) {
                long diffSeconds = now.getEpochSecond() - triggerInstant.getEpochSecond();
                if (diffSeconds < 1800) {
                    log.info(
                            "Thời điểm nhắc trước 15p đã qua nhưng buổi học sắp diễn ra -> Gửi nhắc nhở ngay cho slotId={}",
                            slotId);
                    taskScheduler.execute(() -> {
                        try {
                            emailSenderService.sendReminderEmail(req.getEmail(), req.getSubject(), req.getContent());
                        } catch (Exception e) {
                            log.error("Lỗi khi gửi email nhắc nhở ngay cho slotId=" + slotId + ": " + e.getMessage(),
                                    e);
                        }
                    });
                    return true;
                }
                log.warn("Thời gian nhắc nhở ({}) đã qua quá lâu so với hiện tại ({}), bỏ qua slotId={}",
                        req.getReminderTime(), now, slotId);
                return false;
            }

            log.info("Lên lịch gửi mail nhắc nhở slotId={}, thời điểm gửi: {} (sau {} giây)",
                    slotId, triggerInstant, triggerInstant.getEpochSecond() - now.getEpochSecond());

            ScheduledFuture<?> future = taskScheduler.schedule(() -> {
                try {
                    log.info("Bắt đầu kích hoạt gửi email nhắc nhở cho slotId={} tới email={}", slotId, req.getEmail());
                    emailSenderService.sendReminderEmail(req.getEmail(), req.getSubject(), req.getContent());
                } catch (Exception e) {
                    log.error("Lỗi khi gửi email nhắc nhở slotId=" + slotId + ": " + e.getMessage(), e);
                } finally {
                    scheduledJobs.remove(slotId);
                }
            }, triggerInstant);

            scheduledJobs.put(slotId, future);
            return true;

        } catch (Exception e) {
            log.error("Lỗi khi lập lịch nhắc nhở slotId=" + slotId + ": " + e.getMessage(), e);
            return false;
        }
    }

    public boolean cancelReminder(Long slotId) {
        if (slotId == null) {
            return false;
        }
        ScheduledFuture<?> existing = scheduledJobs.remove(slotId);
        if (existing != null) {
            boolean cancelled = existing.cancel(false);
            log.info("Đã hủy job nhắc nhở cũ cho slotId={}, kết quả={}", slotId, cancelled);
            return cancelled;
        }
        return false;
    }

    public int getActiveJobCount() {
        return scheduledJobs.size();
    }
}
