package com.ocr.javafx.util;

import com.ocr.javafx.config.AppEnv;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.sql.DriverManager;

public class HibernateUtil {

    private static final String DEFAULT_NEON_POOLER_URL =
            "jdbc:postgresql://ep-morning-cell-a1e51jgd-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0&tcpKeepAlive=true&connectTimeout=20&loginTimeout=20&reWriteBatchedInserts=true";
    private static final String DEFAULT_NEON_DIRECT_URL =
            "jdbc:postgresql://ep-morning-cell-a1e51jgd.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0&tcpKeepAlive=true&connectTimeout=20&loginTimeout=20&reWriteBatchedInserts=true";
    private static final String DEFAULT_NEON_USER = "neondb_owner";
    private static final String DEFAULT_NEON_PASS = "npg_v2DGzmBT4roA";

    private static volatile SessionFactory sessionFactory;

    private static String resolveDbUrl() {
        String envUrl = AppEnv.get("DB_URL");
        if (envUrl == null || envUrl.isBlank()
                || envUrl.contains("localhost")
                || envUrl.contains("127.0.0.1")
                || envUrl.equals("DB_URL")) {
            return DEFAULT_NEON_POOLER_URL;
        }
        if (!envUrl.contains("prepareThreshold=")) {
            envUrl += (envUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }
        if (!envUrl.contains("tcpKeepAlive=")) {
            envUrl += "&tcpKeepAlive=true";
        }
        if (!envUrl.contains("connectTimeout=")) {
            envUrl += "&connectTimeout=20&loginTimeout=20&reWriteBatchedInserts=true";
        }
        return envUrl;
    }

    private static String resolveDbUser() {
        String envUrl = AppEnv.get("DB_URL");
        if (envUrl == null || envUrl.contains("localhost") || envUrl.contains("127.0.0.1")) {
            return DEFAULT_NEON_USER;
        }
        String u = AppEnv.get("DB_USER");
        return (u == null || u.isBlank() || u.equals("postgres")) ? DEFAULT_NEON_USER : u;
    }

    private static String resolveDbPassword() {
        String envUrl = AppEnv.get("DB_URL");
        if (envUrl == null || envUrl.contains("localhost") || envUrl.contains("127.0.0.1")) {
            return DEFAULT_NEON_PASS;
        }
        String p = AppEnv.get("DB_PASSWORD");
        if (p == null || p.isBlank() || p.contains("your_") || p.equals("cheinquanh23092904")) {
            return DEFAULT_NEON_PASS;
        }
        return p;
    }

    private static synchronized SessionFactory buildSessionFactoryWithRetry() {
        if (sessionFactory != null) {
            return sessionFactory;
        }

        String primaryUrl = resolveDbUrl();
        String dbUser = resolveDbUser();
        String dbPassword = resolveDbPassword();
        String[] urlsToTry = { primaryUrl, DEFAULT_NEON_POOLER_URL, DEFAULT_NEON_POOLER_URL, DEFAULT_NEON_DIRECT_URL };

        Throwable lastError = null;
        for (int attempt = 0; attempt < urlsToTry.length; attempt++) {
            String currentUrl = urlsToTry[attempt];
            try {
                Class.forName("org.postgresql.Driver");
                DriverManager.setLoginTimeout(20);

                Configuration config = new Configuration().configure();
                config.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
                config.setProperty("hibernate.connection.url", currentUrl);
                config.setProperty("hibernate.connection.username", dbUser);
                config.setProperty("hibernate.connection.password", dbPassword);
                config.setProperty("hibernate.connection.pool_size", "20");
                config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
                config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
                config.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
                config.setProperty("hibernate.hbm2ddl.auto", "none");
                config.setProperty("hibernate.jdbc.batch_size", "30");
                config.setProperty("hibernate.order_inserts", "true");
                config.setProperty("hibernate.order_updates", "true");

                SessionFactory sf = config.buildSessionFactory();
                // Kiểm tra nhanh 1 kết nối để đảm bảo pool đã sẵn sàng
                try (var session = sf.openSession()) {
                    session.doWork(conn -> {});
                }
                sessionFactory = sf;
                return sessionFactory;
            } catch (Throwable ex) {
                lastError = ex;
                System.err.println("Thử kết nối CSDL lần " + (attempt + 1) + " (" + currentUrl + ") thất bại: " + ex.getMessage());
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.err.println("Không thể khởi tạo kết nối Hibernate sau nhiều lần thử.");
        if (lastError != null) {
            lastError.printStackTrace();
        }
        throw new RuntimeException("Không thể kết nối đến máy chủ CSDL. Vui lòng kiểm tra kết nối mạng!", lastError);
    }

    public static SessionFactory getSessionFactory() {
        SessionFactory sf = sessionFactory;
        if (sf == null) {
            sf = buildSessionFactoryWithRetry();
        }
        return sf;
    }
}
