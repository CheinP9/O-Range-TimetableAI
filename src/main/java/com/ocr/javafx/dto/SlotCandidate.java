package com.ocr.javafx.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record SlotCandidate(
        String slotId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String displayText
) {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static SlotCandidate of(String slotId, LocalDate date, LocalTime startTime, LocalTime endTime, int durationMinutes) {
        String dayVi = toVietnameseDay(date.getDayOfWeek());
        String display = String.format("%s (%s) | %s - %s (%d phút)",
                dayVi,
                date.format(DATE_FMT),
                startTime.format(TIME_FMT),
                endTime.format(TIME_FMT),
                durationMinutes);
        return new SlotCandidate(slotId, date, startTime, endTime, durationMinutes, display);
    }

    public static String toVietnameseDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
    }
}
