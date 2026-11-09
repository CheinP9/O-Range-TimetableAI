package com.ocr.javafx.repository;

import com.ocr.javafx.entity.ScheduleSlot;
import com.ocr.javafx.enums.ScheduleStatus;
import com.ocr.javafx.enums.ScheduleType;
import com.ocr.javafx.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class ScheduleSlotRepository {

    private static final List<ScheduleType> AI_STUDY_TYPES = List.of(ScheduleType.AI_PLAN, ScheduleType.STUDY);

    public void saveAll(List<ScheduleSlot> slots) throws Exception {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            for (int i = 0; i < slots.size(); i++) {
                ScheduleSlot slot = slots.get(i);
                sanitizeSlot(slot);
                slot.setStatus(ScheduleStatus.PERSISTED);
                if (slot.getId() == null) {
                    session.persist(slot);
                } else {
                    ScheduleSlot managed = (ScheduleSlot) session.merge(slot);
                    slot.setId(managed.getId());
                }

                if ((i + 1) % 20 == 0) {
                    session.flush();
                    session.clear();
                }
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            throw e;
        }
    }

    public void saveChanges(
            List<ScheduleSlot> slotsToSave,
            List<ScheduleSlot> slotsToDelete
    ) throws Exception {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();

            try {
                // 1. Xóa các slot người dùng đã xác nhận xóa TRƯỚC TIÊN
                if (slotsToDelete != null) {
                    for (ScheduleSlot slot : slotsToDelete) {
                        if (slot.getId() == null) {
                            continue;
                        }

                        ScheduleSlot managed = session.get(ScheduleSlot.class, slot.getId());
                        if (managed != null) {
                            if (managed.getPlan() != null && managed.getPlan().getSlots() != null) {
                                managed.getPlan().getSlots().remove(managed);
                            }
                            managed.setPlan(null);
                            managed.setUser(null);
                            session.remove(managed);
                        }
                    }
                }

                // Đẩy các lệnh xóa xuống DB trước khi merge
                session.flush();

                // 2. Lưu các slot mới và cập nhật các slot hiện có (không merge các slot nằm trong danh sách xóa)
                if (slotsToSave != null) {
                    for (ScheduleSlot slot : slotsToSave) {
                        if (slot.getId() != null && slotsToDelete != null
                                && slotsToDelete.stream().anyMatch(d -> slot.getId().equals(d.getId()))) {
                            continue;
                        }

                        slot.setStatus(ScheduleStatus.PERSISTED);
                        sanitizeSlot(slot);
                        if (slot.getId() == null) {
                            session.persist(slot);
                        } else {
                            ScheduleSlot managed = session.merge(slot);
                            slot.setId(managed.getId());
                        }
                    }
                }

                // 3. Xác nhận toàn bộ giao dịch
                transaction.commit();

            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw e;
            }
        }
    }

    public List<ScheduleSlot> findByUserIdOrderByDateAndStart(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ScheduleSlot s LEFT JOIN FETCH s.plan WHERE s.user.id = :userId ORDER BY s.date ASC, s.startTime ASC";
            Query<ScheduleSlot> query = session.createQuery(hql, ScheduleSlot.class);
            query.setParameter("userId", userId);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<ScheduleSlot> findByPlanIdBeforeDate(Long planId, LocalDate beforeDate) {
        if (planId == null || beforeDate == null) {
            return Collections.emptyList();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ScheduleSlot s WHERE s.plan.id = :planId AND s.date < :beforeDate ORDER BY s.date ASC, s.startTime ASC";
            return session.createQuery(hql, ScheduleSlot.class)
                    .setParameter("planId", planId)
                    .setParameter("beforeDate", beforeDate)
                    .getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public void save(ScheduleSlot slot) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            slot.setStatus(ScheduleStatus.PERSISTED);
            if (slot.getId() == null) {
                session.persist(slot);
            } else {
                ScheduleSlot managed = (ScheduleSlot) session.merge(slot);
                slot.setId(managed.getId());
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    public void delete(ScheduleSlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            ScheduleSlot managed = session.get(ScheduleSlot.class, slot.getId());
            if (managed != null) {
                session.remove(managed);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    public int deleteByPlanIdAndWeek(Long planId, LocalDate start, LocalDate end) {
        if (planId == null || start == null || end == null) {
            return 0;
        }
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            String hql = "delete from ScheduleSlot s where s.plan.id = :planId and s.date between :start and :end";
            int affected = session.createMutationQuery(hql)
                    .setParameter("planId", planId)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .executeUpdate();
            transaction.commit();
            return affected;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return 0;
        }
    }

    public List<ScheduleSlot> findByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ScheduleSlot ls LEFT JOIN FETCH ls.plan WHERE ls.user.id = :userId ORDER BY (ls.endTime - ls.startTime) DESC";
            Query<ScheduleSlot> query = session.createQuery(hql, ScheduleSlot.class);
            query.setParameter("userId", userId);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public double sumTotalHoursByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT s.startTime, s.endTime FROM ScheduleSlot s " +
                    "WHERE s.user.id = :userId " +
                    "AND s.scheduleType IN (:scheduleTypes) " +
                    "AND s.plan IS NOT NULL " +
                    "AND s.completed = true";

            List<Object[]> results = session.createQuery(hql, Object[].class)
                    .setParameter("userId", userId)
                    .setParameter("scheduleTypes", AI_STUDY_TYPES)
                    .getResultList();

            double totalMinutes = 0;

            for (Object[] row : results) {
                java.time.LocalTime start = (java.time.LocalTime) row[0];
                java.time.LocalTime end = (java.time.LocalTime) row[1];

                totalMinutes += java.time.Duration.between(start, end)
                        .toMinutes();
            }

            return totalMinutes / 60.0;
        } catch (Exception e) {
            throw new RuntimeException("Không thể tính tổng giờ tự học.", e);
        }
    }

    public List<java.time.LocalDate> findDistinctDatesByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT DISTINCT s.date FROM ScheduleSlot s " +
                    "WHERE s.user.id = :userId " +
                    "AND s.scheduleType IN (:scheduleTypes) " +
                    "AND s.plan IS NOT NULL " +
                    "AND s.completed = true " +
                    "ORDER BY s.date DESC";

            return session.createQuery(hql, java.time.LocalDate.class)
                    .setParameter("userId", userId)
                    .setParameter("scheduleTypes", AI_STUDY_TYPES)
                    .getResultList();
        }
    }

    public List<Object[]> findDailyCompletionStatus(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT s.date, " +
                    "MIN(CASE WHEN s.completed = true THEN 1 ELSE 0 END) " +
                    "FROM ScheduleSlot s " +
                    "WHERE s.user.id = :userId " +
                    "AND s.scheduleType IN (:scheduleTypes) " +
                    "AND s.plan IS NOT NULL " +
                    "GROUP BY s.date " +
                    "ORDER BY s.date DESC";

            return session.createQuery(hql, Object[].class)
                    .setParameter("userId", userId)
                    .setParameter("scheduleTypes", AI_STUDY_TYPES)
                    .getResultList();
        }
    }

    private void sanitizeSlot(ScheduleSlot slot) {
        if (slot == null) {
            return;
        }
        if (slot.getTopic() != null) {
            slot.setTopic(safeTruncate(slot.getTopic(), 255));
        }
        if (slot.getSubTopic() != null) {
            slot.setSubTopic(safeTruncate(slot.getSubTopic(), 255));
        }
        if (slot.getResourceUrl() != null) {
            slot.setResourceUrl(safeTruncate(slot.getResourceUrl(), 255));
        }
    }

    private static String safeTruncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (maxLength > 0 && trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}