package com.ocr.javafx.service;

import com.ocr.javafx.dto.LearningPlanDTO;
import com.ocr.javafx.dto.request.LearningPlanRequest;
import com.ocr.javafx.dto.response.LearningPlanResponse;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.enums.LearningPlanStatus;
import com.ocr.javafx.repository.LearningPlanRepository;
import com.ocr.javafx.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LearningPlanService {
    private final LearningPlanRepository repository;
    private final ScheduleSlotService slotService;

    public LearningPlanService(LearningPlanRepository repository, ScheduleSlotService slotService) {
        this.repository = repository;
        this.slotService = slotService;
    }

    public LearningPlanResponse createLearningPlan(User user, LearningPlanRequest request) {
        if (request.getTitle() == null) {
            return new LearningPlanResponse(false, "Title can not be empty");
        }
        LearningPlan plan = new LearningPlan();
        plan.setUser(user);
        plan.setTitle(request.getTitle());
        plan.setGoal(request.getGoal());
        plan.setIntensity(request.getIntensity());
        plan.setDomain(request.getDomain());
        plan.setSkills(request.getSkills());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        plan.setProgress(0);
        plan.setDurationDays(request.getDurationDays());
        int target = (request.getTargetSessions() != null && request.getTargetSessions() > 0)
                ? request.getTargetSessions()
                : (request.getDurationDays() != null && request.getDurationDays() > 0 ? request.getDurationDays() : 10);
        plan.setTargetSessions(target);
        plan.setStatus(LearningPlanStatus.NOT_STARTED);
        repository.save(plan);
        return new LearningPlanResponse(true, "Learning Plan created successfully");
    }

    public LearningPlanResponse getAllPlans(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<LearningPlan> rawPlans = repository.findByUserId(session, userId);
            List<LearningPlanDTO> dtos = rawPlans.stream()
                    .map(plan -> convertToDTO(session, plan))
                    .collect(Collectors.toList());

            return new LearningPlanResponse(true, "Fetched successfully", dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return new LearningPlanResponse(false, "Lỗi khi tải danh sách: " + e.getMessage());
        }
    }

    public List<LearningPlan> getPlansForDropdown(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return repository.findByUserId(session, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return java.util.Collections.emptyList();
        }
    }

    private LearningPlanDTO convertToDTO(Session session, LearningPlan plan) {
        long generatedTotal = repository.countTotalSlots(session, plan.getId());
        long completed = repository.countCompletedSlots(session, plan.getId());
        int requiredSessions = plan.getEffectiveTargetSessions();
        long denominator = requiredSessions > 0 ? requiredSessions : generatedTotal;

        int progress = denominator > 0
                ? (int) Math.min(100, Math.round(((double) completed / denominator) * 100))
                : 0;

        LearningPlanStatus expectedStatus;
        if (completed == 0 && generatedTotal == 0) {
            expectedStatus = LearningPlanStatus.NOT_STARTED;
        } else if (progress < 100) {
            expectedStatus = LearningPlanStatus.IN_PROGRESS;
        } else {
            expectedStatus = LearningPlanStatus.COMPLETED;
        }

        if (plan.getProgress() == null || plan.getProgress() != progress || plan.getStatus() != expectedStatus) {
            plan.setProgress(progress);
            plan.setStatus(expectedStatus);
            repository.updateProgressAndStatus(plan.getId(), progress, expectedStatus);
        }

        String startedDate = "N/A";
        LocalDate startDateLocal = plan.getStartDate();
        if (startDateLocal == null && plan.getCreatedAt() != null) {
            startDateLocal = plan.getCreatedAt().toLocalDate();
        }
        if (startDateLocal != null) {
            startedDate = startDateLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            startDateLocal = LocalDate.now();
            startedDate = startDateLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

        long remainingDays = 0;
        if (startDateLocal != null && plan.getDurationDays() != null) {
            LocalDate endDate = startDateLocal.plusDays(plan.getDurationDays());
            LocalDate today = LocalDate.now();
            remainingDays = ChronoUnit.DAYS.between(today, endDate);
            if (remainingDays < 0) remainingDays = 0;
        }

        List<String> skillsList = new ArrayList<>();
        if (plan.getSkills() != null) {
            skillsList.addAll(plan.getSkills());
        }

        return new LearningPlanDTO(
                plan.getId(),
                plan.getTitle(),
                plan.getDomain(),
                plan.getGoal(),
                progress,
                plan.getDurationDays(),
                plan.getIntensity(),
                skillsList,
                (int) remainingDays,
                startedDate,
                (int) completed,
                (int) denominator
        );
    }

    public void deletePlanById(Long id) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            LearningPlan plan = session.get(LearningPlan.class, id);
            if (plan != null) {
                session.remove(plan);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    public LearningPlan getPlanDetails(Long planId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return repository.findByIdWithSlots(session, planId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<LearningPlan> getActivePlans(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<LearningPlan> rawPlans = repository.findByUserId(session, userId);
            for (LearningPlan plan : rawPlans) {
                convertToDTO(session, plan);
            }
        } catch (Exception ignored) {
        }
        return repository.findByUserIdAndStatus(userId, LearningPlanStatus.IN_PROGRESS);
    }

    public LearningPlanDTO updateLearningPlan(Long planId, String title, String domain, String goal,
                                              Integer durationDays, Integer targetSessions, String intensity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            LearningPlan plan = session.get(LearningPlan.class, planId);
            if (plan == null) {
                return null;
            }
            if (title != null && !title.trim().isEmpty()) {
                plan.setTitle(title.trim());
            }
            if (domain != null) {
                plan.setDomain(domain.trim());
            }
            if (goal != null) {
                plan.setGoal(goal.trim());
            }
            if (durationDays != null && durationDays > 0) {
                plan.setDurationDays(durationDays);
            }
            if (targetSessions != null && targetSessions > 0) {
                plan.setTargetSessions(targetSessions);
            }
            if (intensity != null && !intensity.trim().isEmpty()) {
                plan.setIntensity(intensity.trim());
            }
            session.merge(plan);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
            return null;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LearningPlan refreshed = session.get(LearningPlan.class, planId);
            return refreshed != null ? convertToDTO(session, refreshed) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int calculateAndUpdateProgress(Long planId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LearningPlan plan = session.get(LearningPlan.class, planId);
            long generatedTotal = repository.countTotalSlots(session, planId);
            long completed = repository.countCompletedSlots(session, planId);

            int requiredSessions = plan != null ? plan.getEffectiveTargetSessions() : (int) generatedTotal;
            long denominator = requiredSessions > 0 ? requiredSessions : generatedTotal;

            if (denominator <= 0) {
                repository.updateProgressAndStatus(planId, 0, LearningPlanStatus.NOT_STARTED);
                return 0;
            }
            int progress = (int) Math.min(100, Math.round(((double) completed / denominator) * 100));

            LearningPlanStatus status;
            if (completed == 0 && generatedTotal == 0) {
                status = LearningPlanStatus.NOT_STARTED;
            } else if (progress < 100) {
                status = LearningPlanStatus.IN_PROGRESS;
            } else {
                status = LearningPlanStatus.COMPLETED;
            }
            repository.updateProgressAndStatus(planId, progress, status);
            return progress;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

}