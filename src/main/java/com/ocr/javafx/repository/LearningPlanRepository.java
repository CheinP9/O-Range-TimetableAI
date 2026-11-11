package com.ocr.javafx.repository;

import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.enums.LearningPlanStatus;
import com.ocr.javafx.enums.ScheduleType;
import com.ocr.javafx.util.HibernateUtil;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LearningPlanRepository {

    private static final List<ScheduleType> STUDY_TYPES = List.of(ScheduleType.AI_PLAN, ScheduleType.STUDY);

    public List<LearningPlan> findByUserId(Session session, Long userId) {
        String hql = "SELECT DISTINCT lp FROM LearningPlan lp " +
                "LEFT JOIN FETCH lp.skills " +
                "WHERE lp.user.id = :userId";
        return session.createQuery(hql, LearningPlan.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * Lấy tổng số slot và số slot đã hoàn thành của tất cả kế hoạch thuộc userId chỉ trong 1 truy vấn duy nhất.
     * Map trả về: planId -> long[]{ totalSlots, completedSlots }
     */
    public Map<Long, long[]> findSlotCountsByUserId(Session session, Long userId) {
        String hql = "SELECT s.plan.id, COUNT(s), COALESCE(SUM(CASE WHEN s.completed = true THEN 1 ELSE 0 END), 0) " +
                "FROM ScheduleSlot s " +
                "WHERE s.user.id = :userId " +
                "AND s.plan IS NOT NULL " +
                "AND s.scheduleType IN (:scheduleTypes) " +
                "GROUP BY s.plan.id";
        List<Object[]> rows = session.createQuery(hql, Object[].class)
                .setParameter("userId", userId)
                .setParameter("scheduleTypes", STUDY_TYPES)
                .getResultList();

        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            Long planId = (Long) row[0];
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long completed = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            map.put(planId, new long[]{ total, completed });
        }
        return map;
    }

    public long[] findSlotCountsByPlanId(Session session, Long planId) {
        String hql = "SELECT COUNT(s), COALESCE(SUM(CASE WHEN s.completed = true THEN 1 ELSE 0 END), 0) " +
                "FROM ScheduleSlot s " +
                "WHERE s.plan.id = :planId " +
                "AND s.scheduleType IN (:scheduleTypes)";
        Object[] row = session.createQuery(hql, Object[].class)
                .setParameter("planId", planId)
                .setParameter("scheduleTypes", STUDY_TYPES)
                .uniqueResult();
        if (row == null) {
            return new long[]{ 0L, 0L };
        }
        long total = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long completed = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        return new long[]{ total, completed };
    }

    public void save(LearningPlan plan) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(plan);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    public LearningPlan findByIdWithSlots(Session session, Long planId) {
        session.createQuery("SELECT lp FROM LearningPlan lp LEFT JOIN FETCH lp.skills WHERE lp.id = :id", LearningPlan.class)
                .setParameter("id", planId)
                .uniqueResult();
        String hql = "SELECT lp FROM LearningPlan lp LEFT JOIN FETCH lp.slots WHERE lp.id = :id";
        return session.createQuery(hql, LearningPlan.class)
                .setParameter("id", planId)
                .uniqueResult();
    }

    public long countByUserIdAndStatus(Long userId, LearningPlanStatus status) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(lp) FROM LearningPlan lp WHERE lp.user.id = :userId AND lp.status = :status";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("userId", userId);
            query.setParameter("status", status);
            Long result = query.uniqueResult();
            return result != null ? result : 0L;
        }
    }

    public long countByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(lp) FROM LearningPlan lp WHERE lp.user.id = :userId";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("userId", userId);
            Long result = query.uniqueResult();
            return result != null ? result : 0L;
        }
    }

    public List<LearningPlan> findByUserIdAndStatus(Long userId, LearningPlanStatus status) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = """
                SELECT DISTINCT lp
                FROM LearningPlan lp
                LEFT JOIN FETCH lp.skills
                WHERE lp.user.id = :userId AND lp.status = :status
            """;
            TypedQuery<LearningPlan> query = session.createQuery(hql, LearningPlan.class);
            query.setParameter("userId", userId);
            query.setParameter("status", status);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return java.util.Collections.emptyList();
        }
    }

    public long countTotalSlots(Session session, Long planId) {
        String hql = "SELECT COUNT(s) FROM ScheduleSlot s " +
                "WHERE s.plan.id = :planId " +
                "AND s.scheduleType IN (:scheduleTypes)";
        Long count = session.createQuery(hql, Long.class)
                .setParameter("planId", planId)
                .setParameter("scheduleTypes", STUDY_TYPES)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    public long countCompletedSlots(Session session, Long planId) {
        String hql = "SELECT COUNT(s) FROM ScheduleSlot s " +
                "WHERE s.plan.id = :planId " +
                "AND s.scheduleType IN (:scheduleTypes) " +
                "AND s.completed = true";
        Long count = session.createQuery(hql, Long.class)
                .setParameter("planId", planId)
                .setParameter("scheduleTypes", STUDY_TYPES)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    public void updateProgressAndStatus(Long planId, int progress, LearningPlanStatus status) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.createMutationQuery("UPDATE LearningPlan lp SET lp.progress = :progress, lp.status = :status WHERE lp.id = :id")
                    .setParameter("progress", progress)
                    .setParameter("status", status)
                    .setParameter("id", planId)
                    .executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }
}
