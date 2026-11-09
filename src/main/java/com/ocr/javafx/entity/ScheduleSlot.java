package com.ocr.javafx.entity;

import com.ocr.javafx.enums.ScheduleStatus;
import com.ocr.javafx.enums.ScheduleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "schedule_slots")
@Getter
@Setter
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "topic", columnDefinition = "TEXT")
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType = ScheduleType.AI_PLAN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ScheduleStatus status = ScheduleStatus.PERSISTED;

    @Column(name = "is_recurring")
    private Boolean recurring = false;

    @Column(name = "recurring_end_date")
    private LocalDate recurringEndDate;

    @Column(name = "subTopic", columnDefinition = "TEXT")
    private String subTopic;

    @Column(name = "resourceUrl", columnDefinition = "TEXT")
    private String resourceUrl;

    private boolean completed;

    @Column(name = "reminder_enabled")
    private Boolean reminderEnabled = true;

    @Column(name = "reminder_minutes")
    private Integer reminderMinutes = 15;

    @Column(name = "reminder_message", columnDefinition = "TEXT")
    private String reminderMessage;

    @Column(name = "excluded_dates", columnDefinition = "TEXT")
    private String excludedDates;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = true)
    private LearningPlan plan;

    public boolean isRecurring() {
        return Boolean.TRUE.equals(recurring);
    }

    public void setRecurring( boolean recurring ) {
        this.recurring = recurring;
    }

    public boolean isDraft() {
        return status == ScheduleStatus.DRAFT;
    }

    public boolean isAiPlan() {
        return scheduleType == ScheduleType.AI_PLAN
                || (scheduleType == ScheduleType.STUDY && plan != null);
    }

    public boolean isPersonal() {
        return !isAiPlan();
    }

    public boolean isLearningPlanStudy() {
        return isAiPlan() && plan != null;
    }

    public boolean isReminderEnabled() {
        return reminderEnabled != null && reminderEnabled;
    }

    public void setReminderEnabled(Boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public int getReminderMinutes() {
        return (reminderMinutes != null && reminderMinutes > 0) ? reminderMinutes : 15;
    }

    public void setReminderMinutes(Integer reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public String getReminderMessage() {
        return reminderMessage;
    }

    public void setReminderMessage(String reminderMessage) {
        this.reminderMessage = reminderMessage;
    }

    public String getExcludedDates() {
        return excludedDates;
    }

    public void setExcludedDates(String excludedDates) {
        this.excludedDates = excludedDates;
    }

    public java.util.Set<LocalDate> getExcludedDatesSet() {
        if (excludedDates == null || excludedDates.isBlank()) {
            return new java.util.HashSet<>();
        }
        java.util.Set<LocalDate> set = new java.util.HashSet<>();
        for (String s : excludedDates.split(",")) {
            if (!s.isBlank()) {
                try {
                    set.add(LocalDate.parse(s.trim()));
                } catch (Exception ignored) {}
            }
        }
        return set;
    }

    public void addExcludedDate(LocalDate date) {
        if (date == null) return;
        java.util.Set<LocalDate> set = getExcludedDatesSet();
        set.add(date);
        this.excludedDates = set.stream().map(LocalDate::toString).collect(java.util.stream.Collectors.joining(","));
    }

    public boolean isDateExcluded(LocalDate date) {
        if (date == null) return false;
        return getExcludedDatesSet().contains(date);
    }
}