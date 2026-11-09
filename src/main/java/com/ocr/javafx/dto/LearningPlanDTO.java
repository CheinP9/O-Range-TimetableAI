package com.ocr.javafx.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class LearningPlanDTO {
    private Long id;
    private String title;
    private String domain;
    private String goal;
    private Integer progress;
    private Integer durationDays;
    private String intensity;
    private List<String> skills;
    private Integer remainingDays;
    private String startedDate;
    private Integer completedSessions;
    private Integer targetSessions;

    public LearningPlanDTO(Long id, String title, String domain, String goal, Integer progress,
                           Integer durationDays, String intensity, List<String> skills,
                           Integer remainingDays, String startedDate) {
        this(id, title, domain, goal, progress, durationDays, intensity, skills, remainingDays, startedDate, 0, durationDays);
    }
}
