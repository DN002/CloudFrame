package dev.cloudframe.bukkit.power;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.bukkit.Location;
import org.bukkit.World;

import dev.cloudframe.common.storage.Database;

/**
 * Minimal SQLite-backed storage for Bukkit prototype power cells.
 *
 * <p>Fabric stores energy in block entity NBT; Bukkit doesn't have that yet,
 * so this is a stopgap to test the shared power network logic.</p>
 */
public final class BukkitPowerCellRepository {

    public static void ensureSchema() {
        Database.run(conn -> {
            try (var stmt = conn.createStatement()) {
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
            }
        });
    }

    public long getStoredCfe(Location loc) {
        if (loc == null) return 0L;
        World w = loc.getWorld();
        if (w == null) return 0L;

        String world = w.getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        final long[] out = new long[] { 0L };
        Database.run(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT stored_cfe FROM power_cells WHERE world=? AND x=? AND y=? AND z=?"
            )) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        out[0] = 0L;
                        return;
                    }
                    out[0] = Math.max(0L, rs.getLong(1));
                }
            } catch (SQLException ignored) {
                out[0] = 0L;
            }
        });
        return out[0];
    }

    public void setStoredCfe(Location loc, long storedCfe) {
        if (loc == null) return;
        World w = loc.getWorld();
        if (w == null) return;

        String world = w.getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        long v = Math.max(0L, storedCfe);

        Database.run(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO power_cells(world,x,y,z,stored_cfe) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(world,x,y,z) DO UPDATE SET stored_cfe=excluded.stored_cfe"
            )) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setLong(5, v);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }
}
