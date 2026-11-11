package com.ocr.javafx.service;

import com.ocr.javafx.dto.SlotCandidate;
import com.ocr.javafx.entity.ScheduleSlot;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SlotExtractionService {

    public static final int START_HOUR = 6;
    public static final int END_HOUR = 23;

    private static final LocalTime MORNING_START = LocalTime.of(6, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);

    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 0);

    private static final LocalTime EVENING_START = LocalTime.of(19, 0);
    private static final LocalTime EVENING_END = LocalTime.of(23, 0);

    public record TimeInterval(LocalTime start, LocalTime end) {
        public long durationMinutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    /**
     * Quét tuần mục tiêu [weekStart, weekEnd], loại trừ toàn bộ các slot bận (bao gồm cả lịch cá nhân lặp tuần),
     * và trả về danh sách các SlotCandidate có độ dài {@code sessionDurationMinutes}.
     * Mặc định lấy thời điểm hiện tại để lọc bỏ các ngày/giờ đã qua trong tuần hiện tại.
     */
    public List<SlotCandidate> extractFreeSlots(
            LocalDate weekStart,
            LocalDate weekEnd,
            List<ScheduleSlot> busySlots,
            int sessionDurationMinutes
    ) {
        return extractFreeSlots(weekStart, weekEnd, busySlots, sessionDurationMinutes, LocalDateTime.now());
    }

    /**
     * Quét tuần mục tiêu kèm chỉ định thời điểm hiện tại (phục vụ cả kiểm thử logic và phân biệt tuần hiện tại/tương lai).
     */
    public List<SlotCandidate> extractFreeSlots(
            LocalDate weekStart,
            LocalDate weekEnd,
            List<ScheduleSlot> busySlots,
            int sessionDurationMinutes,
            LocalDateTime currentDateTime
    ) {
        int validDuration = Math.max(30, sessionDurationMinutes);
        List<SlotCandidate> candidates = new ArrayList<>();
        int counter = 1;

        LocalDateTime now = currentDateTime != null ? currentDateTime : LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            // Đối với tuần hiện tại: Tuyệt đối không xếp vào các ngày trong quá khứ
            if (date.isBefore(today)) {
                continue;
            }

            List<TimeInterval> freeWindows = new ArrayList<>();
            freeWindows.add(new TimeInterval(MORNING_START, MORNING_END));
            freeWindows.add(new TimeInterval(AFTERNOON_START, AFTERNOON_END));
            freeWindows.add(new TimeInterval(EVENING_START, EVENING_END));

            List<TimeInterval> busyOnDay = collectBusyIntervalsForDate(date, busySlots);

            // Đối với ngày hôm nay: Khung giờ đã qua trước thời điểm hiện tại coi như bận
            if (date.equals(today)) {
                busyOnDay.add(new TimeInterval(LocalTime.MIN, nowTime));
            }

            for (TimeInterval busy : busyOnDay) {
                freeWindows = subtractInterval(freeWindows, busy);
            }

            for (TimeInterval window : freeWindows) {
                LocalTime cursor = alignToCleanStart(window.start());
                while (cursor.isBefore(window.end())
                        && Duration.between(cursor, window.end()).toMinutes() >= validDuration) {
                    LocalTime slotEnd = cursor.plusMinutes(validDuration);
                    if (!slotEnd.isAfter(cursor) || slotEnd.isAfter(window.end())) {
                        break;
                    }
                    String slotId = "SLOT_" + counter++;
                    candidates.add(SlotCandidate.of(slotId, date, cursor, slotEnd, validDuration));

                    // Tiến tới giờ chẵn tiếp theo để tránh đè dòng trên GridPane và tạo khoảng nghỉ tự nhiên
                    LocalTime nextCursor = nextNonOverlappingStart(slotEnd);
                    if (!nextCursor.isAfter(cursor)) {
                        break;
                    }
                    cursor = nextCursor;
                }
            }
        }

        return candidates;
    }

    /**
     * Tìm một khung giờ trống gần nhất trong tuần có cùng thời lượng để dời buổi học AI khi xảy ra xung đột.
     */
    public Optional<SlotCandidate> findNearestFreeSlot(
            LocalDate preferredDate,
            LocalTime preferredStart,
            int durationMinutes,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<ScheduleSlot> allOccupiedSlots
    ) {
        List<SlotCandidate> candidates = extractFreeSlots(weekStart, weekEnd, allOccupiedSlots, durationMinutes);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime targetDateTime = LocalDateTime.of(
                preferredDate != null ? preferredDate : weekStart,
                preferredStart != null ? preferredStart : LocalTime.of(8, 0)
        );

        return candidates.stream()
                .min(Comparator.comparingLong(c -> {
                    LocalDateTime candidateDT = LocalDateTime.of(c.date(), c.startTime());
                    return Math.abs(Duration.between(targetDateTime, candidateDT).toMinutes());
                }));
    }

    private List<TimeInterval> collectBusyIntervalsForDate(LocalDate targetDate, List<ScheduleSlot> busySlots) {
        List<TimeInterval> result = new ArrayList<>();
        if (busySlots == null) {
            return result;
        }
        for (ScheduleSlot slot : busySlots) {
            if (slot == null || slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
                continue;
            }
            boolean matchesDate = Objects.equals(slot.getDate(), targetDate) && !slot.isDateExcluded(targetDate);
            boolean matchesRecurring = slot.isPersonal()
                    && slot.isRecurring()
                    && slot.getDate().getDayOfWeek() == targetDate.getDayOfWeek()
                    && !targetDate.isBefore(slot.getDate())
                    && (slot.getRecurringEndDate() == null || !targetDate.isAfter(slot.getRecurringEndDate()))
                    && !slot.isDateExcluded(targetDate);

            if (matchesDate || matchesRecurring) {
                // Mở rộng biên theo dòng lưới giờ để không bị 2 thẻ đè chung 1 ô giờ trên GridPane
                LocalTime effectiveStart = LocalTime.of(slot.getStartTime().getHour(), 0);
                LocalTime effectiveEnd = slot.getEndTime();
                if (effectiveEnd.getMinute() > 0 && effectiveEnd.getHour() < END_HOUR) {
                    effectiveEnd = LocalTime.of(effectiveEnd.getHour() + 1, 0);
                }
                if (effectiveEnd.isAfter(effectiveStart)) {
                    result.add(new TimeInterval(effectiveStart, effectiveEnd));
                }
            }
        }
        return result;
    }

    private static List<TimeInterval> subtractInterval(List<TimeInterval> freeList, TimeInterval busy) {
        List<TimeInterval> next = new ArrayList<>();
        for (TimeInterval free : freeList) {
            // Không giao nhau
            if (!busy.start().isBefore(free.end()) || !busy.end().isAfter(free.start())) {
                next.add(free);
                continue;
            }
            // Phần còn lại bên trái
            if (busy.start().isAfter(free.start())) {
                next.add(new TimeInterval(free.start(), busy.start()));
            }
            // Phần còn lại bên phải
            if (busy.end().isBefore(free.end())) {
                next.add(new TimeInterval(busy.end(), free.end()));
            }
        }
        return next;
    }

    private static LocalTime alignToCleanStart(LocalTime time) {
        int minute = time.getMinute();
        if (minute == 0 || minute == 30) {
            return time.withSecond(0).withNano(0);
        }
        if (minute < 30) {
            return LocalTime.of(time.getHour(), 30);
        }
        if (time.getHour() < END_HOUR) {
            return LocalTime.of(time.getHour() + 1, 0);
        }
        return time;
    }

    private static LocalTime nextNonOverlappingStart(LocalTime slotEnd) {
        if (slotEnd.getMinute() == 0) {
            return slotEnd;
        }
        if (slotEnd.getHour() < END_HOUR) {
            return LocalTime.of(slotEnd.getHour() + 1, 0);
        }
        return LocalTime.of(END_HOUR, 0);
    }
}

