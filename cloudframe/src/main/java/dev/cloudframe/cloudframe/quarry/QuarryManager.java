package dev.cloudframe.cloudframe.quarry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.listeners.ControllerVisualChunkListener;
import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.Region;
import dev.cloudframe.cloudframe.storage.Database;
import dev.cloudframe.cloudframe.util.CustomBlocks;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class QuarryManager {

    private static final Debug debug = DebugManager.get(QuarryManager.class);

    private final List<Quarry> quarries = new ArrayList<>();

    // Controllers that have been placed but not yet finalized into a quarry.
    // This keeps entity-only controllers from disappearing on chunk refresh.
    private final Map<Location, Integer> unregisteredControllers = new HashMap<>();

    private ControllerVisualManager visualManager;

    public void initVisuals(JavaPlugin plugin) {
        if (visualManager != null) return;
        visualManager = new ControllerVisualManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(new ControllerVisualChunkListener(this), plugin);
    }

    public void shutdownVisuals() {
        if (visualManager != null) {
            visualManager.shutdown();
        }
    }

    public ControllerVisualManager visualsManager() {
        return visualManager;
    }

    public java.util.List<Location> controllerLocationsInChunk(org.bukkit.Chunk chunk) {
        java.util.List<Location> out = new java.util.ArrayList<>();
        for (Quarry q : quarries) {
            Location loc = q.getController();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                out.add(loc);
            }
        }

        for (Location loc : unregisteredControllers.keySet()) {
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                out.add(loc);
            }
        }
        return out;
    }

    public void markUnregisteredController(Location loc, int controllerYaw) {
        loc = norm(loc);
        unregisteredControllers.put(loc, controllerYaw);
        debug.log("markUnregisteredController", "Marked unregistered controller at " + loc + " yaw=" + controllerYaw + " (count=" + unregisteredControllers.size() + ")");
        if (visualManager != null) {
            visualManager.ensureController(loc, controllerYaw);
        }

        // Ensure tubes around it update immediately.
        refreshAdjacentTubes(loc);
    }

    public void unmarkUnregisteredController(Location loc) {
        loc = norm(loc);
        boolean removed = unregisteredControllers.remove(loc) != null;
        debug.log("unmarkUnregisteredController", "Unmarked unregistered controller at " + loc + " removed=" + removed + " (count=" + unregisteredControllers.size() + ")");

        // Ensure tubes around it disconnect immediately.
        refreshAdjacentTubes(loc);
    }

    public void register(Quarry q) {
        quarries.add(q);
        debug.log("register", "Registered quarry for owner=" + q.getOwner() +
                " controller=" + q.getController());

        unmarkUnregisteredController(q.getController());

        if (visualManager != null) {
            visualManager.ensureController(q.getController(), q.getControllerYaw());
        }

        // Ensure tubes around it update immediately.
        refreshAdjacentTubes(q.getController());
    }

    public int getControllerYaw(Location controllerLoc) {
        controllerLoc = norm(controllerLoc);
        Quarry q = getByController(controllerLoc);
        if (q != null) return q.getControllerYaw();
        Integer yaw = unregisteredControllers.get(controllerLoc);
        return yaw != null ? yaw : 0;
    }

    public boolean hasControllerAt(Location controllerLoc) {
        controllerLoc = norm(controllerLoc);
        if (getByController(controllerLoc) != null) return true;
        return unregisteredControllers.containsKey(controllerLoc);
    }

    public void tickAll(boolean shouldLog) {
        if (shouldLog) {
            debug.log("tickAll", "Ticking " + quarries.size() + " quarries");
        }

        for (Quarry q : quarries) {
            try {
                q.tick(shouldLog);
            } catch (Exception ex) {
                debug.log("tickAll", "Exception ticking quarry owner=" + q.getOwner() +
                        " msg=" + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public List<Quarry> all() {
        return quarries;
    }

    public void saveAll() {
        debug.log("saveAll", "Saving " + quarries.size() + " quarries to database");

        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM quarries");

            var ps = conn.prepareStatement("""
                INSERT INTO quarries
                (owner, world, ax, ay, az, bx, by, bz, controllerX, controllerY, controllerZ, active, controllerYaw, silkTouch, speedLevel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);

            for (Quarry q : quarries) {

                debug.log("saveAll", "Saving quarry owner=" + q.getOwner() +
                        " controller=" + q.getController());

                ps.setString(1, q.getOwner().toString());
                ps.setString(2, q.getWorld().getName());

                // Save original marker positions
                ps.setInt(3, q.getPosA().getBlockX());
                ps.setInt(4, q.getPosA().getBlockY());
                ps.setInt(5, q.getPosA().getBlockZ());

                ps.setInt(6, q.getPosB().getBlockX());
                ps.setInt(7, q.getPosB().getBlockY());
                ps.setInt(8, q.getPosB().getBlockZ());

                // Save controller
                ps.setInt(9, q.getController().getBlockX());
                ps.setInt(10, q.getController().getBlockY());
                ps.setInt(11, q.getController().getBlockZ());

                ps.setInt(12, q.isActive() ? 1 : 0);

                ps.setInt(13, q.getControllerYaw());

                ps.setInt(14, q.hasSilkTouchAugment() ? 1 : 0);
                ps.setInt(15, q.getSpeedAugmentLevel());

                ps.addBatch();
            }

            ps.executeBatch();

            // Persist unregistered (unfinalized) controllers too.
            conn.createStatement().executeUpdate("DELETE FROM unregistered_controllers");
            var cps = conn.prepareStatement(
                "INSERT INTO unregistered_controllers (world, x, y, z, yaw) VALUES (?, ?, ?, ?, ?)"
            );

            for (Map.Entry<Location, Integer> entry : unregisteredControllers.entrySet()) {
                Location loc = entry.getKey();
                if (loc == null || loc.getWorld() == null) continue;
                int yaw = entry.getValue() != null ? entry.getValue() : 0;

                cps.setString(1, loc.getWorld().getName());
                cps.setInt(2, loc.getBlockX());
                cps.setInt(3, loc.getBlockY());
                cps.setInt(4, loc.getBlockZ());
                cps.setInt(5, yaw);
                cps.addBatch();
            }

            cps.executeBatch();
        });

        debug.log("saveAll", "Finished saving quarries");
    }

    public void loadAll() {
        debug.log("loadAll", "Loading quarries from database");

        quarries.clear(); // prevent duplicates on reload
        unregisteredControllers.clear();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM quarries");

            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner"));
                String world = rs.getString("world");

                var w = Bukkit.getWorld(world);
                if (w == null) {
                    debug.log("loadAll", "World not found: " + world + " — skipping quarry");
                    continue;
                }

                // Load original marker positions
                Location a = new Location(w, rs.getInt("ax"), rs.getInt("ay"), rs.getInt("az"));
                Location b = new Location(w, rs.getInt("bx"), rs.getInt("by"), rs.getInt("bz"));

                // Load controller
                Location controller = new Location(
                        w,
                        rs.getInt("controllerX"),
                        rs.getInt("controllerY"),
                        rs.getInt("controllerZ")
                );

                int controllerYaw = 0;
                try {
                    controllerYaw = rs.getInt("controllerYaw");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }

                boolean silkTouch = false;
                try {
                    silkTouch = rs.getInt("silkTouch") == 1;
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }

                int speedLevel = 0;
                try {
                    speedLevel = rs.getInt("speedLevel");
                } catch (java.sql.SQLException ignored) {
                    // Older DBs won't have this column.
                }

                // Rebuild region (normalized + cached)
                Region region = new Region(a, b);

                // Rebuild full vertical region exactly like WrenchListener
                int topY = Math.max(a.getBlockY(), b.getBlockY());
                int bottomY = w.getMinHeight();

                region = new Region(
                    new Location(w, region.minX(), bottomY, region.minZ()),
                    new Location(w, region.maxX(), topY, region.maxZ())
                );

                Quarry q = new Quarry(owner, a, b, region, controller, controllerYaw);

                q.setSilkTouchAugment(silkTouch);
                q.setSpeedAugmentLevel(speedLevel);

                boolean active = rs.getInt("active") == 1;
                q.setActive(active);

                debug.log("loadAll", "Loaded quarry owner=" + owner +
                        " controller=" + controller +
                        " active=" + active);

                // Migration cleanup: older versions used NOTE_BLOCKs for controllers.
                // Entity-only controller should not leave blocks behind.
                if (CustomBlocks.isLegacyControllerNoteBlock(controller.getBlock())) {
                    controller.getBlock().setType(Material.AIR, false);
                }

                register(q);
            }

            // Load unregistered controllers (placed but not finalized).
            try {
                var crs = conn.createStatement().executeQuery("SELECT * FROM unregistered_controllers");
                while (crs.next()) {
                    String world = crs.getString("world");
                    int x = crs.getInt("x");
                    int y = crs.getInt("y");
                    int z = crs.getInt("z");
                    int yaw = 0;
                    try {
                        yaw = crs.getInt("yaw");
                    } catch (java.sql.SQLException ignored) {
                        // Older DBs may not have yaw.
                    }

                    var w = Bukkit.getWorld(world);
                    if (w == null) {
                        debug.log("loadAll", "World not found: " + world + " — skipping unregistered controller");
                        continue;
                    }

                    Location loc = new Location(w, x, y, z);
                    // Don't duplicate controllers that are already registered as quarries.
                    if (getByController(loc) != null) continue;
                    markUnregisteredController(loc, yaw);
                }
            } catch (java.sql.SQLException ex) {
                // Table may not exist in very old DBs; ignore.
                debug.log("loadAll", "No unregistered_controllers table found; skipping");
            }
        });

        debug.log("loadAll", "Finished loading quarries");
    }

    public void remove(Quarry q) {
        quarries.remove(q);
        debug.log("remove", "Removed quarry owner=" + q.getOwner() +
                " controller=" + q.getController());

        if (visualManager != null) {
            visualManager.removeController(q.getController());
        }

        // Ensure tubes around it disconnect immediately.
        refreshAdjacentTubes(q.getController());
    }

    public Quarry getByController(Location loc) {
        for (Quarry q : quarries) {
            if (q.getController().getWorld().equals(loc.getWorld()) &&
                q.getController().getBlockX() == loc.getBlockX() &&
                q.getController().getBlockY() == loc.getBlockY() &&
                q.getController().getBlockZ() == loc.getBlockZ()) {

                debug.log("getByController", "Found quarry for controller=" + loc);
                return q;
            }
        }
        debug.log("getByController", "No quarry found for controller=" + loc);
        return null;
    }

    private static Location norm(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    private static void refreshAdjacentTubes(Location controllerLoc) {
        if (controllerLoc == null || controllerLoc.getWorld() == null) return;
        if (CloudFrameRegistry.tubes() == null || CloudFrameRegistry.tubes().visualsManager() == null) return;

        final Vector[] dirs = new Vector[] {
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0),
            new Vector(0, 1, 0),
            new Vector(0, -1, 0),
            new Vector(0, 0, 1),
            new Vector(0, 0, -1)
        };

        for (Vector v : dirs) {
            Location adj = controllerLoc.clone().add(v);
            if (CloudFrameRegistry.tubes().getTube(adj) != null) {
                CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(adj);
            }
        }
    }
}
