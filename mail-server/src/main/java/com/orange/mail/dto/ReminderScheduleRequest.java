package com.orange.mail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReminderScheduleRequest {

    @NotNull(message = "slotId không được để trống")
    private Long slotId;

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không đúng định dạng")
    private String email;

    @NotBlank(message = "subject không được để trống")
    private String subject;

    @NotBlank(message = "content không được để trống")
    private String content;

    @NotBlank(message = "reminderTime không được để trống")
    private String reminderTime; // Định dạng yyyy-MM-dd'T'HH:mm:ss

    public ReminderScheduleRequest() {
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(String reminderTime) {
        this.reminderTime = reminderTime;
    }
}

