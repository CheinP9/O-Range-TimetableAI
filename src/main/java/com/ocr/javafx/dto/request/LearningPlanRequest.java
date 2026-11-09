package com.ocr.javafx.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlanRequest {
    private String title;
    private String goal;
    private String intensity;
    private List<String> skills;
    private String domain;
    private Integer durationDays;
    private Integer targetSessions;
    private LocalDate startDate;

    public LearningPlanRequest(String title, String goal, String intensity, List<String> skills, String domain, Integer durationDays) {
        this(title, goal, intensity, skills, domain, durationDays, durationDays, LocalDate.now());
    }

    public LearningPlanRequest(String title, String goal, String intensity, List<String> skills, String domain, Integer durationDays, Integer targetSessions) {
        this(title, goal, intensity, skills, domain, durationDays, targetSessions, LocalDate.now());
    }

    public LearningPlanRequest(String title, String goal, String intensity, List<String> skills, String domain, Integer durationDays, LocalDate startDate) {
        this(title, goal, intensity, skills, domain, durationDays, durationDays, startDate);
    }
}
