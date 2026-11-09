
package com.ocr.javafx.util;

import com.ocr.javafx.config.AppEnv;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {

    private static final SessionFactory SESSION_FACTORY =
            buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            Class.forName("org.postgresql.Driver");

            // Đọc cấu hình từ AppEnv
            String dbUrl = AppEnv.require("DB_URL");
            String dbUser = AppEnv.require("DB_USER");
            String dbPassword = AppEnv.getOrDefault(
                    "DB_PASSWORD", ""
            );

            // Đọc cấu hình Hibernate
            Configuration config =
                    new Configuration().configure();

            // Ghi đè các giá trị mẫu trong hibernate.cfg.xml
            config.setProperty(
                    "hibernate.connection.driver_class",
                    "org.postgresql.Driver"
            );

            config.setProperty(
                    "hibernate.connection.url",
                    dbUrl
            );

            config.setProperty(
                    "hibernate.connection.username",
                    dbUser
            );

            config.setProperty(
                    "hibernate.connection.password",
                    dbPassword
            );

            SessionFactory sessionFactory = config.buildSessionFactory();
            applySchemaPatches(sessionFactory);
            return sessionFactory;

        } catch (Throwable ex) {
            System.err.println(
                    "Không thể khởi tạo kết nối Hibernate."
            );
            ex.printStackTrace();

            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void applySchemaPatches(SessionFactory sf) {
        try (var session = sf.openSession()) {
            session.doWork(connection -> {
                boolean originalAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(true);
                    try (var st = connection.createStatement()) {
                        // 1. PL/pgSQL block: xóa mọi check constraint liên quan đến schedule_type và chuyển đổi các cột text sang TEXT
                        String plsql = """
                            DO $$
                            DECLARE
                                r RECORD;
                                con_name TEXT;
                            BEGIN
                                FOR con_name IN (
                                    SELECT con.conname
                                    FROM pg_constraint con
                                    JOIN pg_class rel ON rel.oid = con.conrelid
                                    WHERE rel.relname = 'schedule_slots'
                                      AND con.contype = 'c'
                                      AND pg_get_constraintdef(con.oid) LIKE '%schedule_type%'
                                ) LOOP
                                    BEGIN
                                        EXECUTE 'ALTER TABLE schedule_slots DROP CONSTRAINT IF EXISTS ' || quote_ident(con_name);
                                    EXCEPTION WHEN OTHERS THEN NULL;
                                    END;
                                END LOOP;

                                FOR r IN (
                                    SELECT column_name 
                                    FROM information_schema.columns 
                                    WHERE table_name = 'schedule_slots' 
                                      AND lower(column_name) IN ('topic', 'subtopic', 'sub_topic', 'resourceurl', 'resource_url', 'reminder_note')
                                ) LOOP
                                    BEGIN
                                        EXECUTE format('ALTER TABLE schedule_slots ALTER COLUMN %I TYPE TEXT', r.column_name);
                                    EXCEPTION WHEN OTHERS THEN NULL;
                                    END;
                                END LOOP;
                            END $$;
                        """;
                        try {
                            st.execute(plsql);
                        } catch (Throwable t) {
                            System.err.println("Schema patch notice (PL/pgSQL): " + t.getMessage());
                        }

                        // 2. Explicit individual fallback statements & cấu hình các cột Email Reminder
                        String[] singleStatements = {
                            "ALTER TABLE schedule_slots DROP CONSTRAINT IF EXISTS schedule_slots_schedule_type_check",
                            "ALTER TABLE schedule_slots ADD COLUMN IF NOT EXISTS recurring_end_date DATE",
                            "ALTER TABLE learning_plans ADD COLUMN IF NOT EXISTS start_date DATE",
                            "ALTER TABLE schedule_slots ALTER COLUMN topic TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN \"subTopic\" TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN subtopic TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN sub_topic TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN \"resourceUrl\" TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN resourceurl TYPE TEXT",
                            "ALTER TABLE schedule_slots ALTER COLUMN resource_url TYPE TEXT",
                            "ALTER TABLE schedule_slots ADD COLUMN IF NOT EXISTS reminder_enabled BOOLEAN DEFAULT TRUE",
                            "ALTER TABLE schedule_slots ADD COLUMN IF NOT EXISTS reminder_minutes INTEGER DEFAULT 15",
                            "ALTER TABLE schedule_slots ADD COLUMN IF NOT EXISTS reminder_message TEXT",
                            "ALTER TABLE schedule_slots ADD COLUMN IF NOT EXISTS excluded_dates TEXT",
                            "ALTER TABLE learning_plans ADD COLUMN IF NOT EXISTS target_sessions INTEGER",
                            "UPDATE schedule_slots SET recurring_end_date = (date + INTERVAL '28 days')::date WHERE is_recurring = true AND recurring_end_date IS NULL AND date IS NOT NULL"
                        };

                        for (String sql : singleStatements) {
                            try {
                                st.execute(sql);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } finally {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            System.err.println("Schema patch warning: " + t.getMessage());
        }
    }

    public static SessionFactory getSessionFactory() {
        return SESSION_FACTORY;
    }
}
