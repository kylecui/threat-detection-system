package com.threatdetection.stream.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 数据库配置类
 * 为stream-processing服务提供数据库连接
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final String DB_URL = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://postgres:5432/threat_detection");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "threat_user");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "threat_password");

    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            logger.debug("Database connection established successfully");
            return conn;
        } catch (ClassNotFoundException e) {
            logger.error("PostgreSQL driver not found", e);
            throw new SQLException("PostgreSQL driver not found", e);
        }
    }

    /**
     * 测试数据库连接
     * @return 连接是否成功
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5); // 5秒超时
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }
}