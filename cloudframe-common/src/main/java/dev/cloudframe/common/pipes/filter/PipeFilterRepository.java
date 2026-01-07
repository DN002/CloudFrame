package dev.cloudframe.common.pipes.filter;

import dev.cloudframe.common.storage.Database;

import java.util.HashMap;
import java.util.Map;

/**
 * Platform-agnostic persistence for pipe-side filters.
 *
 * Backed by the shared SQLite {@code pipe_filters} table.
 */
public final class PipeFilterRepository {

    private PipeFilterRepository() {
    }

    public static Map<PipeFilterKey, PipeFilterState> loadAll() {
        Map<PipeFilterKey, PipeFilterState> out = new HashMap<>();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM pipe_filters");
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int side = rs.getInt("side");

                int mode = 0;
                try {
                    mode = rs.getInt("mode");
                } catch (Throwable ignored) {
                    mode = 0;
                }

                String items = null;
                try {
                    items = rs.getString("items");
                } catch (Throwable ignored) {
                    items = null;
                }

                String[] itemIds = PipeFilterCodec.deserializeItemIds(items);
                PipeFilterKey key = new PipeFilterKey(world, x, y, z, side);
                out.put(key, new PipeFilterState(mode, itemIds));
            }
        });

        return out;
    }

    public static void upsert(PipeFilterKey key, PipeFilterState state) {
        if (key == null || state == null) return;

        String items = PipeFilterCodec.serializeItemIds(state.copyItemIds());
        int mode = state.mode();

        Database.run(conn -> {
            var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO pipe_filters (world, x, y, z, side, mode, items) VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, key.worldId());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setInt(5, key.side());
            ps.setInt(6, mode);
            ps.setString(7, items);
            ps.executeUpdate();
        });
    }

    public static void delete(PipeFilterKey key) {
        if (key == null) return;

        Database.run(conn -> {
            var ps = conn.prepareStatement(
                "DELETE FROM pipe_filters WHERE world = ? AND x = ? AND y = ? AND z = ? AND side = ?"
            );
            ps.setString(1, key.worldId());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setInt(5, key.side());
            ps.executeUpdate();
        });
    }

    public static void deleteAllAt(String worldId, int x, int y, int z) {
        Database.run(conn -> {
            var ps = conn.prepareStatement("DELETE FROM pipe_filters WHERE world = ? AND x = ? AND y = ? AND z = ?");
            ps.setString(1, worldId);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        });
    }
}
