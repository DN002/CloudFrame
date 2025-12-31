package dev.cloudframe.common.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            var ps = conn.prepareStatement("INSERT INTO quarries (owner, world, ax, ay, az, bx, by, bz, controllerX, controllerY, controllerZ, active, controllerYaw) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            for (Quarry q : quarries) {
                Object ctrl = q.getController();
                Object world = platform.worldOf(ctrl);
                ps.setString(1, q.getOwner().toString());
                ps.setString(2, world != null ? world.toString() : "");
                ps.setInt(3, platform.blockX(q.getPosA()));
                ps.setInt(4, platform.blockY(q.getPosA()));
                ps.setInt(5, platform.blockZ(q.getPosA()));
                ps.setInt(6, platform.blockX(q.getPosB()));
                ps.setInt(7, platform.blockY(q.getPosB()));
                ps.setInt(8, platform.blockZ(q.getPosB()));
                ps.setInt(9, platform.blockX(ctrl));
                ps.setInt(10, platform.blockY(ctrl));
                ps.setInt(11, platform.blockZ(ctrl));
                ps.setInt(12, q.isActive() ? 1 : 0);
                ps.setInt(13, q.getControllerYaw());
                ps.addBatch();
            }
            ps.executeBatch();
        });
    }

    public void loadAll() {
        quarries.clear();
        debug.log("loadAll", "Loading quarries (stub)");
        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM quarries");
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner"));
                String worldName = rs.getString("world");
                Object world = platform.worldByName(worldName);
                Object a = platform.createLocation(world, rs.getInt("ax"), rs.getInt("ay"), rs.getInt("az"));
                Object b = platform.createLocation(world, rs.getInt("bx"), rs.getInt("by"), rs.getInt("bz"));
                Object controller = platform.createLocation(world, rs.getInt("controllerX"), rs.getInt("controllerY"), rs.getInt("controllerZ"));
                int yaw = rs.getInt("controllerYaw");

                if (world == null || a == null || b == null || controller == null) continue;

                Region region = new Region(world, platform.blockX(a), platform.blockY(a), platform.blockZ(a), world, platform.blockX(b), platform.blockY(b), platform.blockZ(b));
                Quarry q = new Quarry(owner, a, b, region, controller, yaw, platform);
                boolean active = rs.getInt("active") == 1;
                q.setActive(active);
                register(q);

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("loadAll", "Loaded quarry owner=" + owner + " controller=" + controller + " active=" + active);
                }
            }
        });
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
}
