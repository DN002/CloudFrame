package dev.cloudframe.common.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFlags;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.Region;

/**
 * Platform-agnostic stub for managing quarries.
 */
public class QuarryManager {

    private static final Debug debug = DebugManager.get(QuarryManager.class);

    private final List<Quarry> quarries = new ArrayList<>();
    private final QuarryPlatform platform;

    public QuarryManager(QuarryPlatform platform) {
        this.platform = platform;
    }

    public void register(Quarry q) {
        quarries.add(q);
    }

    public void remove(Quarry q) {
        // Remove glass frame before removing quarry
        q.removeGlassFrame();
        // Ensure we don't leave forced chunks behind.
        q.setChunkLoadingEnabled(false);
        quarries.remove(q);
    }

    public List<Quarry> all() { return quarries; }

    public void tickAll(boolean shouldLog) {
        for (Quarry q : quarries) {
            q.tick(shouldLog);
        }
    }

    public void saveAll() {
        debug.log("saveAll", "Saving " + quarries.size() + " quarries (stub)");
        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM quarries");
            var ps = conn.prepareStatement("""
                INSERT INTO quarries
                (owner, ownerName, world, ax, ay, az, bx, by, bz, controllerX, controllerY, controllerZ, active, controllerYaw, silkTouch, speedLevel, outputRoundRobin, redstoneMode, chunkLoadingEnabled, silentMode, frameMinX, frameMinZ, frameMaxX, frameMaxZ)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);
            for (Quarry q : quarries) {
                bindQuarryInsert(ps, q);
                ps.addBatch();
            }
            ps.executeBatch();
        });
    }

    /**
     * Persist a single quarry row (identified by controller location) without rewriting the full table.
     * This keeps UI toggles efficient and reduces cross-platform drift.
     */
    public void saveQuarry(Quarry q) {
        if (q == null) return;
        Object ctrl = q.getController();
        if (ctrl == null) return;

        Database.run(conn -> {
            Object world = platform.worldOf(ctrl);
            String worldName = world != null ? platform.worldName(world) : "";
            int cx = platform.blockX(ctrl);
            int cy = platform.blockY(ctrl);
            int cz = platform.blockZ(ctrl);

            // Table has no PRIMARY KEY; enforce uniqueness by controller location.
            var del = conn.prepareStatement("DELETE FROM quarries WHERE world = ? AND controllerX = ? AND controllerY = ? AND controllerZ = ?");
            del.setString(1, worldName);
            del.setInt(2, cx);
            del.setInt(3, cy);
            del.setInt(4, cz);
            del.executeUpdate();

            var ins = conn.prepareStatement("""
                INSERT INTO quarries
                (owner, ownerName, world, ax, ay, az, bx, by, bz, controllerX, controllerY, controllerZ, active, controllerYaw, silkTouch, speedLevel, outputRoundRobin, redstoneMode, chunkLoadingEnabled, silentMode, frameMinX, frameMinZ, frameMaxX, frameMaxZ)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);
            bindQuarryInsert(ins, q);
            ins.executeUpdate();
        });
    }

    /** Delete a single quarry row (identified by controller location). */
    public void deleteQuarry(Quarry q) {
        if (q == null) return;
        Object ctrl = q.getController();
        if (ctrl == null) return;
        deleteQuarryByController(ctrl);
    }

    /** Delete a single quarry row (identified by controller location). */
    public void deleteQuarryByController(Object controllerLoc) {
        if (controllerLoc == null) return;
        Object world = platform.worldOf(controllerLoc);
        String worldName = world != null ? platform.worldName(world) : "";
        int cx = platform.blockX(controllerLoc);
        int cy = platform.blockY(controllerLoc);
        int cz = platform.blockZ(controllerLoc);

        Database.run(conn -> {
            var del = conn.prepareStatement("DELETE FROM quarries WHERE world = ? AND controllerX = ? AND controllerY = ? AND controllerZ = ?");
            del.setString(1, worldName);
            del.setInt(2, cx);
            del.setInt(3, cy);
            del.setInt(4, cz);
            del.executeUpdate();
        });
    }

    private void bindQuarryInsert(java.sql.PreparedStatement ps, Quarry q) throws java.sql.SQLException {
        Object ctrl = q.getController();
        Object world = platform.worldOf(ctrl);
        ps.setString(1, q.getOwner().toString());
        ps.setString(2, q.getOwnerName());
        ps.setString(3, world != null ? platform.worldName(world) : "");
        ps.setInt(4, platform.blockX(q.getPosA()));
        ps.setInt(5, platform.blockY(q.getPosA()));
        ps.setInt(6, platform.blockZ(q.getPosA()));
        ps.setInt(7, platform.blockX(q.getPosB()));
        ps.setInt(8, platform.blockY(q.getPosB()));
        ps.setInt(9, platform.blockZ(q.getPosB()));
        ps.setInt(10, platform.blockX(ctrl));
        ps.setInt(11, platform.blockY(ctrl));
        ps.setInt(12, platform.blockZ(ctrl));
        ps.setInt(13, q.isActive() ? 1 : 0);
        ps.setInt(14, q.getControllerYaw());
        ps.setInt(15, q.hasSilkTouchAugment() ? 1 : 0);
        ps.setInt(16, Math.max(0, q.getSpeedAugmentLevel()));
        ps.setInt(17, q.isOutputRoundRobin() ? 1 : 0);
        ps.setInt(18, q.getRedstoneMode());
        ps.setInt(19, q.isChunkLoadingEnabled() ? 1 : 0);
        ps.setInt(20, q.isSilentMode() ? 1 : 0);
        ps.setInt(21, q.frameMinX());
        ps.setInt(22, q.frameMinZ());
        ps.setInt(23, q.frameMaxX());
        ps.setInt(24, q.frameMaxZ());
    }

    public void loadAll() {
        quarries.clear();
        debug.log("loadAll", "Loading quarries (stub)");
        final List<Quarry> savedActiveQuarries = new ArrayList<>();
        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM quarries");
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner"));
                String ownerName = null;
                try {
                    ownerName = rs.getString("ownerName");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                String worldName = rs.getString("world");
                Object world = platform.worldByName(worldName);
                Object a = platform.createLocation(world, rs.getInt("ax"), rs.getInt("ay"), rs.getInt("az"));
                Object b = platform.createLocation(world, rs.getInt("bx"), rs.getInt("by"), rs.getInt("bz"));
                Object controller = platform.createLocation(world, rs.getInt("controllerX"), rs.getInt("controllerY"), rs.getInt("controllerZ"));
                int yaw = 0;
                try {
                    yaw = rs.getInt("controllerYaw");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }

                if (world == null || a == null || b == null || controller == null) continue;

                Region region = new Region(world, platform.blockX(a), platform.blockY(a), platform.blockZ(a), world, platform.blockX(b), platform.blockY(b), platform.blockZ(b));
                Quarry q = new Quarry(owner, ownerName, a, b, region, controller, yaw, platform);

                // Load optional glass-frame bounds (may differ from mining region bounds).
                try {
                    Object fMinXObj = rs.getObject("frameMinX");
                    Object fMinZObj = rs.getObject("frameMinZ");
                    Object fMaxXObj = rs.getObject("frameMaxX");
                    Object fMaxZObj = rs.getObject("frameMaxZ");
                    if (fMinXObj != null && fMinZObj != null && fMaxXObj != null && fMaxZObj != null) {
                        int fMinX = ((Number) fMinXObj).intValue();
                        int fMinZ = ((Number) fMinZObj).intValue();
                        int fMaxX = ((Number) fMaxXObj).intValue();
                        int fMaxZ = ((Number) fMaxZObj).intValue();
                        q.setFrameBounds(fMinX, fMinZ, fMaxX, fMaxZ);
                    }
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have these columns.
                }

                boolean silkTouch = false;
                try {
                    silkTouch = rs.getInt("silkTouch") == 1;
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setSilkTouchAugment(silkTouch);

                int speedLevel = 0;
                try {
                    speedLevel = rs.getInt("speedLevel");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setSpeedAugmentLevel(speedLevel);

                boolean outputRoundRobin = true;
                try {
                    outputRoundRobin = rs.getInt("outputRoundRobin") == 1;
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setOutputRoundRobin(outputRoundRobin);

                int redstoneMode = 0;
                try {
                    redstoneMode = rs.getInt("redstoneMode");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setRedstoneMode(redstoneMode);

                boolean chunkLoadingEnabled = false;
                try {
                    chunkLoadingEnabled = rs.getInt("chunkLoadingEnabled") == 1;
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setChunkLoadingEnabled(chunkLoadingEnabled);

                boolean silentMode = false;
                try {
                    silentMode = rs.getInt("silentMode") == 1;
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }
                q.setSilentMode(silentMode);

                // Always load as paused on startup. The saved 'active' state historically caused
                // quarries to start scanning immediately after restart, which is surprising UX.
                // Players can explicitly resume via the controller GUI.
                boolean active = rs.getInt("active") == 1;
                q.setActive(false);
                register(q);

                if (active) {
                    savedActiveQuarries.add(q);
                }

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("loadAll", "Loaded quarry owner=" + owner + " controller=" + controller + " active(saved)=" + active + " active(loaded)=false");
                }
            }
        });

        // If any quarries were saved as active, rewrite the DB so they remain paused on future restarts.
        if (!savedActiveQuarries.isEmpty()) {
            for (Quarry q : savedActiveQuarries) {
                saveQuarry(q);
            }
        }
    }

    public Quarry getByController(Object controllerLoc) {
        for (Quarry q : quarries) {
            Object c = q.getController();
            if (c == null) continue;
            if (!platform.worldOf(c).equals(platform.worldOf(controllerLoc))) continue;
            if (platform.blockX(c) == platform.blockX(controllerLoc) &&
                platform.blockY(c) == platform.blockY(controllerLoc) &&
                platform.blockZ(c) == platform.blockZ(controllerLoc)) {
                return q;
            }
        }
        return null;
    }

    /**
     * Platform hook: notify quarries that a block changed/was placed at a location.
     * Quarries use this to "actively scan" already-mined sections.
     */
    public void markDirtyBlock(Object world, int x, int y, int z) {
        if (world == null) return;
        for (Quarry q : quarries) {
            if (q == null) continue;
            Region r = q.getRegion();
            if (r == null) continue;
            if (!r.contains(world, x, y, z)) continue;
            q.markDirtyBlock(world, x, y, z);
        }
    }
}
