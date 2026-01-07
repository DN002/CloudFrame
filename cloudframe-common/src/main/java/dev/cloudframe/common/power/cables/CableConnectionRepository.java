package dev.cloudframe.common.power.cables;

import dev.cloudframe.common.storage.Database;

import java.util.HashMap;
import java.util.Map;

/**
 * SQLite repository for cable disabled-side state.
 *
 * Backed by the shared {@code cables} table.
 */
public final class CableConnectionRepository {

    private CableConnectionRepository() {
    }

    public static Map<CableKey, CableConnectionState> loadAll() {
        Map<CableKey, CableConnectionState> result = new HashMap<>();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM cables");
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                int disabled = 0;
                try {
                    disabled = rs.getInt("disabled_sides");
                } catch (Throwable ignored) {
                    disabled = 0;
                }

                CableKey key = new CableKey(world, x, y, z);
                result.put(key, new CableConnectionState(disabled));
            }
        });

        return result;
    }

    public static void upsert(CableKey key, CableConnectionState state) {
        if (key == null) return;

        int mask = state == null ? 0 : state.disabledSidesMask();

        Database.run(conn -> {
            if (mask == 0) {
                var ps = conn.prepareStatement("DELETE FROM cables WHERE world = ? AND x = ? AND y = ? AND z = ?");
                ps.setString(1, key.worldId());
                ps.setInt(2, key.x());
                ps.setInt(3, key.y());
                ps.setInt(4, key.z());
                ps.executeUpdate();
                return;
            }

            var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO cables (world, x, y, z, disabled_sides) VALUES (?, ?, ?, ?, ?)"
            );
            ps.setString(1, key.worldId());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setInt(5, mask);
            ps.executeUpdate();
        });
    }

    public static void delete(CableKey key) {
        if (key == null) return;

        Database.run(conn -> {
            var ps = conn.prepareStatement("DELETE FROM cables WHERE world = ? AND x = ? AND y = ? AND z = ?");
            ps.setString(1, key.worldId());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.executeUpdate();
        });
    }
}
