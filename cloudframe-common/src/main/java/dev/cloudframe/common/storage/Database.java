package dev.cloudframe.common.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Central SQLite handler for CloudFrame.
 * Opens a single connection, initializes tables, and provides a safe run() helper.
 * Platform-agnostic - can be used by Bukkit, Fabric, or any other platform.
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
                    ownerName TEXT,
                    world TEXT NOT NULL,
                    ax INTEGER, ay INTEGER, az INTEGER,
                    bx INTEGER, by INTEGER, bz INTEGER,
                    controllerX INTEGER,
                    controllerY INTEGER,
                    controllerZ INTEGER,
                    active INTEGER DEFAULT 1,
                    controllerYaw INTEGER DEFAULT 0,
                    redstoneMode INTEGER DEFAULT 0,
                    chunkLoadingEnabled INTEGER DEFAULT 0,
                    silentMode INTEGER DEFAULT 0,
                    frameMinX INTEGER,
                    frameMinZ INTEGER,
                    frameMaxX INTEGER,
                    frameMaxZ INTEGER,
                    fortuneLevel INTEGER DEFAULT 0
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

            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN fortuneLevel INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Best-effort migration for output routing mode.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN outputRoundRobin INTEGER DEFAULT 1");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Best-effort migrations for stored glass-frame bounds (perimeter may differ from mining region).
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN frameMinX INTEGER");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN frameMinZ INTEGER");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN frameMaxX INTEGER");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN frameMaxZ INTEGER");
            } catch (SQLException ignored) {
                // Column already exists.
            }

            // Best-effort migrations for controller settings.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN redstoneMode INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN chunkLoadingEnabled INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN silentMode INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }

            // Best-effort migration for storing a friendly owner username.
            try {
                stmt.executeUpdate("ALTER TABLE quarries ADD COLUMN ownerName TEXT");
            } catch (SQLException ignored) {
                // Column already exists.
            }

            // Pipes table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pipes (
                    world TEXT NOT NULL,
                    x INTEGER, y INTEGER, z INTEGER
                );
            """);

            // Best-effort migration for disabled inventory sides.
            try {
                stmt.executeUpdate("ALTER TABLE pipes ADD COLUMN disabled_sides INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Cables table (Cloud Cables per-side disabled external connections)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cables (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    disabled_sides INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                );
            """);

            // Best-effort migration for disabled cable sides.
            try {
                stmt.executeUpdate("ALTER TABLE cables ADD COLUMN disabled_sides INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Power cells table (prototype Bukkit storage for Cloud Cells).
            // Fabric stores cell energy in block entity NBT; Bukkit needs a persistence layer.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS power_cells (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    stored_cfe INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                );
            """);

            // Pipe filters (per-side)
            // items is a serialized 27-slot list of item identifiers.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pipe_filters (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    side INTEGER NOT NULL,
                    mode INTEGER DEFAULT 0,
                    items TEXT,
                    PRIMARY KEY (world, x, y, z, side)
                );
            """);

            // Best-effort migration for pipe filter columns.
            try {
                stmt.executeUpdate("ALTER TABLE pipe_filters ADD COLUMN mode INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }
            try {
                stmt.executeUpdate("ALTER TABLE pipe_filters ADD COLUMN items TEXT");
            } catch (SQLException ignored) {
                // Column already exists (or table is new).
            }

            // Markers table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS markers (
                    player TEXT NOT NULL,
                    world TEXT,
                    ax INTEGER, ay INTEGER, az INTEGER,
                    bx INTEGER, by INTEGER, bz INTEGER
                );
            """);

            // Best-effort migration for dimension-aware markers.
            try {
                stmt.executeUpdate("ALTER TABLE markers ADD COLUMN world TEXT");
            } catch (SQLException ignored) {
                // Column already exists.
            }

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
                    fortuneLevel INTEGER DEFAULT 0,
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

            try {
                stmt.executeUpdate("ALTER TABLE unregistered_controllers ADD COLUMN fortuneLevel INTEGER DEFAULT 0");
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
