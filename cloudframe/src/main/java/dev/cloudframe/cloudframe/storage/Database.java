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
                    active INTEGER DEFAULT 1,
                    controllerYaw INTEGER DEFAULT 0
                );
            """);

            // Best-effort migration for older DBs.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN controllerYaw INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Best-effort migrations for quarry augments.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN silkTouch INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN speedLevel INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Best-effort migration for output routing mode.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN outputRoundRobin INTEGER DEFAULT 1");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

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

            // Unregistered controllers table (entity-only controllers placed but not finalized into a quarry)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS unregistered_controllers (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    yaw INTEGER DEFAULT 0,
                    silkTouch INTEGER DEFAULT 0,
                    speedLevel INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                );
            """);

            // Best-effort migrations for stored controller augments.
            try {
                stmt.executeUpdate("ALTER TABLE unregistered_controllers ADD COLUMN silkTouch INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }

            try {
                stmt.executeUpdate("ALTER TABLE unregistered_controllers ADD COLUMN speedLevel INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }
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
