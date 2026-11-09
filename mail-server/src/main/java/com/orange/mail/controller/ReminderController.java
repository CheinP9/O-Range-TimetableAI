package com.orange.mail.controller;

import com.orange.mail.dto.ApiResponse;
import com.orange.mail.dto.ReminderScheduleRequest;
import com.orange.mail.service.ReminderSchedulerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private static final Logger log = LoggerFactory.getLogger(ReminderController.class);

    private final ReminderSchedulerService reminderSchedulerService;

    public ReminderController(ReminderSchedulerService reminderSchedulerService) {
        this.reminderSchedulerService = reminderSchedulerService;
    }

    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse> scheduleReminder(@Valid @RequestBody ReminderScheduleRequest request) {
        log.info("Nhận yêu cầu lên lịch reminder: slotId={}, reminderTime={}",
                request.getSlotId(), request.getReminderTime());

        boolean scheduled = reminderSchedulerService.scheduleReminder(request);
        if (scheduled) {
            return ResponseEntity.ok(ApiResponse.ok("Đã lên lịch nhắc nhở thành công cho slot " + request.getSlotId()));
        } else {
            return ResponseEntity.ok(ApiResponse.ok("Bỏ qua hoặc không thể lên lịch (thời gian đã qua)"));
        }
    }

    @DeleteMapping("/schedule/{slotId}")
    public ResponseEntity<ApiResponse> cancelReminder(@PathVariable Long slotId) {
        log.info("Nhận yêu cầu hủy lịch nhắc nhở slotId={}", slotId);
        boolean cancelled = reminderSchedulerService.cancelReminder(slotId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xử lý hủy nhắc nhở cho slot " + slotId, Map.of("cancelled", cancelled)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse> getStatus() {
        int activeJobs = reminderSchedulerService.getActiveJobCount();
        return ResponseEntity.ok(ApiResponse.ok("Mail Service đang hoạt động", Map.of("activeJobs", activeJobs)));
    }
}

