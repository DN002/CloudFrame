package dev.cloudframe.cloudframe.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Central SQLite handler for CloudFrame.
 * Opens a single connection, initializes tables, and provides a safe run() helper.
 */
public class Database {

    private static Connection connection;

    public static void init(String path) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + path);

        try (Statement stmt = connection.createStatement()) {

            // Enable foreign key support (SQLite requires this explicitly)
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Schema version table (future-proofing)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER
                );
            """);

            // Insert version 1 if table is empty
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO schema_version (version) VALUES (1);
            """);

            // Quarries table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quarries (
                    owner TEXT NOT NULL,
                    world TEXT NOT NULL,
                    ax INTEGER, ay INTEGER, az INTEGER,
                    bx INTEGER, by INTEGER, bz INTEGER,
                    controllerX INTEGER,
                    controllerY INTEGER,
                    controllerZ INTEGER,
                    active INTEGER DEFAULT 1
                );
            """);

            // Tubes table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tubes (
                    world TEXT NOT NULL,
                    x INTEGER, y INTEGER, z INTEGER
                );
            """);

            // Markers table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS markers (
                    player TEXT NOT NULL,
                    ax INTEGER, ay INTEGER, az INTEGER,
                    bx INTEGER, by INTEGER, bz INTEGER
                );
            """);
        }
    }

    public static Connection get() {
        return connection;
    }

    /**
     * Safe helper for executing SQL with auto-close behavior.
     */
    public static void run(SQLConsumer consumer) {
        try {
            consumer.accept(connection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleanly close the SQLite connection on plugin shutdown.
     */
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    public interface SQLConsumer {
        void accept(Connection conn) throws Exception;
    }
}
