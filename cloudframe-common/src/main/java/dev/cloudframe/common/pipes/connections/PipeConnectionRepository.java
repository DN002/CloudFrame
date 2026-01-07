package dev.cloudframe.common.pipes.connections;

import dev.cloudframe.common.storage.Database;

import java.util.HashMap;
import java.util.Map;

/**
 * SQLite repository for pipe disabled-side state.
 *
 * Backed by the shared {@code pipes} table.
 *
 * Note: Unlike {@code cables}, {@code pipes} is used to persist pipe locations as well.
 * As a result, this repository never deletes pipe rows when the mask becomes 0; it only
 * updates {@code disabled_sides}.
 */
public final class PipeConnectionRepository {

    private PipeConnectionRepository() {
    }

    /**
     * Loads all non-zero disabled-side masks from the pipes table.
     */
    public static Map<PipeKey, PipeConnectionState> loadAllNonZero() {
        Map<PipeKey, PipeConnectionState> result = new HashMap<>();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery(
                "SELECT world, x, y, z, disabled_sides FROM pipes WHERE disabled_sides != 0"
            );
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

                if (disabled == 0) continue;

                PipeKey key = new PipeKey(world, x, y, z);
                result.put(key, new PipeConnectionState(disabled));
            }
        });

        return result;
    }

    /**
     * Persists the disabled-side mask for a pipe row.
     *
     * If the pipe row doesn't exist yet (e.g., freshly placed pipe not yet saved), it will be inserted.
     */
    public static void upsertMask(PipeKey key, int disabledSidesMask) {
        if (key == null) return;

        Database.run(conn -> {
            // pipes table has no primary key; update-first avoids duplicates in normal usage.
            var update = conn.prepareStatement(
                "UPDATE pipes SET disabled_sides = ? WHERE world = ? AND x = ? AND y = ? AND z = ?"
            );
            update.setInt(1, disabledSidesMask);
            update.setString(2, key.worldId());
            update.setInt(3, key.x());
            update.setInt(4, key.y());
            update.setInt(5, key.z());
            int rows = update.executeUpdate();

            if (rows <= 0) {
                var insert = conn.prepareStatement(
                    "INSERT INTO pipes (world, x, y, z, disabled_sides) VALUES (?, ?, ?, ?, ?)"
                );
                insert.setString(1, key.worldId());
                insert.setInt(2, key.x());
                insert.setInt(3, key.y());
                insert.setInt(4, key.z());
                insert.setInt(5, disabledSidesMask);
                insert.executeUpdate();
            }
        });
    }
}
