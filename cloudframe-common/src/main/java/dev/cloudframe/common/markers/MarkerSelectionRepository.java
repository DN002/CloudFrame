package dev.cloudframe.common.markers;

import dev.cloudframe.common.storage.Database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite repository for per-player marker selections.
 *
 * Backed by the shared {@code markers} table.
 */
public final class MarkerSelectionRepository {

    private MarkerSelectionRepository() {
    }

    public static Map<UUID, MarkerSelectionState> loadAll() {
        Map<UUID, MarkerSelectionState> result = new HashMap<>();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM markers");
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player"));

                String worldId;
                try {
                    worldId = rs.getString("world");
                } catch (java.sql.SQLException ignored) {
                    worldId = null;
                }

                int ax = rs.getInt("ax");
                int ay = rs.getInt("ay");
                int az = rs.getInt("az");
                int bx = rs.getInt("bx");
                int by = rs.getInt("by");
                int bz = rs.getInt("bz");

                int minX = Math.min(ax, bx);
                int maxX = Math.max(ax, bx);
                int minZ = Math.min(az, bz);
                int maxZ = Math.max(az, bz);
                int y = ay;

                List<MarkerPos> corners = MarkerSelectionState.cornersFromBounds(minX, y, minZ, maxX, maxZ);
                // Persisted state does not store activation. Preserve existing behavior: load as not activated.
                result.put(playerId, new MarkerSelectionState(corners, y, false, worldId));
            }
        });

        return result;
    }

    public static void saveAll(Map<UUID, MarkerSelectionState> selections) {
        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM markers");
            var ps = conn.prepareStatement(
                "INSERT INTO markers (player, world, ax, ay, az, bx, by, bz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );

            if (selections == null) {
                ps.executeBatch();
                return;
            }

            for (var e : selections.entrySet()) {
                UUID id = e.getKey();
                MarkerSelectionState sel = e.getValue();
                if (id == null || sel == null) continue;
                List<MarkerPos> corners = sel.corners();
                if (corners.size() < 2) continue;

                int minX = corners.get(0).x();
                int maxX = corners.get(0).x();
                int minZ = corners.get(0).z();
                int maxZ = corners.get(0).z();
                int y = corners.get(0).y();

                for (MarkerPos corner : corners) {
                    if (corner == null) continue;
                    minX = Math.min(minX, corner.x());
                    maxX = Math.max(maxX, corner.x());
                    minZ = Math.min(minZ, corner.z());
                    maxZ = Math.max(maxZ, corner.z());
                }

                ps.setString(1, id.toString());
                ps.setString(2, sel.worldId() == null ? "minecraft:overworld" : sel.worldId());
                ps.setInt(3, minX);
                ps.setInt(4, y);
                ps.setInt(5, minZ);
                ps.setInt(6, maxX);
                ps.setInt(7, y);
                ps.setInt(8, maxZ);
                ps.addBatch();
            }

            ps.executeBatch();
        });
    }

    public static void upsert(UUID playerId, MarkerSelectionState sel) {
        if (playerId == null || sel == null) return;
        List<MarkerPos> corners = sel.corners();
        if (corners.size() < 2) return;

        int minX = corners.get(0).x();
        int maxX = corners.get(0).x();
        int minZ = corners.get(0).z();
        int maxZ = corners.get(0).z();
        int y = corners.get(0).y();

        for (MarkerPos corner : corners) {
            if (corner == null) continue;
            minX = Math.min(minX, corner.x());
            maxX = Math.max(maxX, corner.x());
            minZ = Math.min(minZ, corner.z());
            maxZ = Math.max(maxZ, corner.z());
        }

        String worldId = sel.worldId() == null ? "minecraft:overworld" : sel.worldId();

        final int fMinX = minX;
        final int fMaxX = maxX;
        final int fMinZ = minZ;
        final int fMaxZ = maxZ;
        final int fY = y;
        final String fWorldId = worldId;

        Database.run(conn -> {
            var del = conn.prepareStatement("DELETE FROM markers WHERE player = ?");
            del.setString(1, playerId.toString());
            del.executeUpdate();

            var ps = conn.prepareStatement(
                "INSERT INTO markers (player, world, ax, ay, az, bx, by, bz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, playerId.toString());
            ps.setString(2, fWorldId);
            ps.setInt(3, fMinX);
            ps.setInt(4, fY);
            ps.setInt(5, fMinZ);
            ps.setInt(6, fMaxX);
            ps.setInt(7, fY);
            ps.setInt(8, fMaxZ);
            ps.executeUpdate();
        });
    }

    public static void delete(UUID playerId) {
        if (playerId == null) return;
        Database.run(conn -> {
            var del = conn.prepareStatement("DELETE FROM markers WHERE player = ?");
            del.setString(1, playerId.toString());
            del.executeUpdate();
        });
    }
}
