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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LearningPlanService {
    private final LearningPlanRepository repository;
    private final ScheduleSlotService slotService;

    public LearningPlanService(LearningPlanRepository repository, ScheduleSlotService slotService) {
        this.repository = repository;
        this.slotService = slotService;
    }

    public LearningPlanResponse createLearningPlan(User user, LearningPlanRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            return new LearningPlanResponse(false, "Title can not be empty");
        }
        LearningPlan plan = new LearningPlan();
        plan.setUser(user);
        plan.setTitle(request.getTitle().trim());
        plan.setGoal(request.getGoal() != null ? request.getGoal().trim() : "");
        plan.setIntensity(request.getIntensity() != null ? request.getIntensity() : "Medium");
        plan.setDomain(request.getDomain() != null ? request.getDomain().trim() : "");
        plan.setSkills(request.getSkills() != null ? request.getSkills() : new ArrayList<>());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        plan.setProgress(0);
        plan.setDurationDays(request.getDurationDays() != null && request.getDurationDays() > 0 ? request.getDurationDays() : 14);
        int target = (request.getTargetSessions() != null && request.getTargetSessions() > 0)
                ? request.getTargetSessions()
                : (request.getDurationDays() != null && request.getDurationDays() > 0 ? request.getDurationDays() : 10);
        plan.setTargetSessions(target);
        plan.setStatus(LearningPlanStatus.NOT_STARTED);
        repository.save(plan);
        return new LearningPlanResponse(true, "Learning Plan created successfully");
    }

    public LearningPlanResponse getAllPlans(Long userId) {
        if (userId == null) {
            return new LearningPlanResponse(true, "No user", Collections.emptyList());
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<LearningPlan> rawPlans = repository.findByUserId(session, userId);
            Map<Long, long[]> countsMap = repository.findSlotCountsByUserId(session, userId);

            List<LearningPlanDTO> dtos = rawPlans.stream()
                    .map(plan -> {
                        long[] counts = countsMap.getOrDefault(plan.getId(), new long[]{ 0L, 0L });
                        return convertToDTOWithCounts(plan, counts[0], counts[1]);
                    })
                    .collect(Collectors.toList());

            return new LearningPlanResponse(true, "Fetched successfully", dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return new LearningPlanResponse(false, "Lỗi khi tải danh sách: " + e.getMessage(), Collections.emptyList());
        }
    }

    public List<LearningPlan> getPlansForDropdown(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return repository.findByUserId(session, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private LearningPlanDTO convertToDTOWithCounts(LearningPlan plan, long generatedTotal, long completed) {
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

        plan.setProgress(progress);
        plan.setStatus(expectedStatus);

        String startedDate;
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

    /**
     * Xóa learning plan cùng các schedule slot thuộc về nó.
     * Phải xóa slot trước, nếu không ràng buộc khóa ngoại schedule_slots.plan_id
     * sẽ khiến lệnh DELETE learning_plans thất bại và transaction bị rollback.
     *
     * @return true nếu xóa thành công (hoặc plan không còn tồn tại), false nếu lỗi.
     */
    public boolean deletePlanById(Long id) {
        if (id == null) return false;
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            LearningPlan plan = session.get(LearningPlan.class, id);
            if (plan != null) {
                session.createMutationQuery("DELETE FROM ScheduleSlot s WHERE s.plan.id = :planId")
                        .setParameter("planId", id)
                        .executeUpdate();
                session.remove(plan);
            }
            transaction.commit();
            return true;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        }
    }

    public LearningPlan getPlanDetails(Long planId) {
        if (planId == null) return null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return repository.findByIdWithSlots(session, planId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<LearningPlan> getActivePlans(Long userId) {
        if (userId == null) return Collections.emptyList();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<LearningPlan> rawPlans = repository.findByUserId(session, userId);
            Map<Long, long[]> countsMap = repository.findSlotCountsByUserId(session, userId);
            List<LearningPlan> active = new ArrayList<>();
            for (LearningPlan plan : rawPlans) {
                long[] counts = countsMap.getOrDefault(plan.getId(), new long[]{ 0L, 0L });
                convertToDTOWithCounts(plan, counts[0], counts[1]);
                if (plan.getStatus() == LearningPlanStatus.IN_PROGRESS) {
                    active.add(plan);
                }
            }
            return active;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public LearningPlanDTO updateLearningPlan(Long planId, String title, String domain, String goal,
                                              Integer durationDays, Integer targetSessions, String intensity) {
        if (planId == null) return null;
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            LearningPlan plan = session.createQuery(
                    "SELECT lp FROM LearningPlan lp LEFT JOIN FETCH lp.skills WHERE lp.id = :id",
                    LearningPlan.class
            ).setParameter("id", planId).uniqueResult();

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

            long[] counts = repository.findSlotCountsByPlanId(session, planId);
            LearningPlanDTO dto = convertToDTOWithCounts(plan, counts[0], counts[1]);
            transaction.commit();
            return dto;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
            return null;
        }
    }

    public int calculateAndUpdateProgress(Long planId) {
        if (planId == null) return 0;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            LearningPlan plan = session.get(LearningPlan.class, planId);
            long[] counts = repository.findSlotCountsByPlanId(session, planId);
            long generatedTotal = counts[0];
            long completed = counts[1];

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
