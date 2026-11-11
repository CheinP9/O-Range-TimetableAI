package com.ocr.javafx.repository;

import com.ocr.javafx.entity.User;
import com.ocr.javafx.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

public class UserRepository {
    public User findByEmail(String email) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User WHERE lower(email) = lower(:email)";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("email", email != null ? email.trim() : "");
            query.setMaxResults(1);
            return query.uniqueResult();
        }
    }

    public void save(User user) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.merge(user);
            tx.commit();
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception ignored) {
                }
            }
            throw e;
        }
    }

    public void update(User user) {
        save(user);
    }

    public User findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User WHERE id = :id";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("id", id);
            return query.uniqueResult();
        }
    }

}
