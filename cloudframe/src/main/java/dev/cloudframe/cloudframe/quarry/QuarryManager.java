package dev.cloudframe.cloudframe.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import dev.cloudframe.cloudframe.util.Region;
import dev.cloudframe.cloudframe.storage.Database;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class QuarryManager {

    private static final Debug debug = DebugManager.get(QuarryManager.class);

    private final List<Quarry> quarries = new ArrayList<>();

    public void register(Quarry q) {
        quarries.add(q);
        debug.log("register", "Registered quarry for owner=" + q.getOwner() +
                " controller=" + q.getController());
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
                (owner, world, ax, ay, az, bx, by, bz, controllerX, controllerY, controllerZ, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

                ps.addBatch();
            }

            ps.executeBatch();
        });

        debug.log("saveAll", "Finished saving quarries");
    }

    public void loadAll() {
        debug.log("loadAll", "Loading quarries from database");

        quarries.clear(); // prevent duplicates on reload

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

                // Rebuild region (normalized + cached)
                Region region = new Region(a, b);

                // Flatten region to controller Y (matches WrenchListener behavior)
                int y = controller.getBlockY();
                region = new Region(
                        new Location(w, region.minX(), y, region.minZ()),
                        new Location(w, region.maxX(), y, region.maxZ())
                );

                Quarry q = new Quarry(owner, a, b, region, controller);

                boolean active = rs.getInt("active") == 1;
                q.setActive(active);

                debug.log("loadAll", "Loaded quarry owner=" + owner +
                        " controller=" + controller +
                        " active=" + active);

                // Restore controller block if missing
                if (controller.getBlock().getType() != Material.COPPER_BLOCK) {
                    controller.getBlock().setType(Material.COPPER_BLOCK);
                    debug.log("loadAll", "Restored missing controller block at " + controller);
                }

                register(q);
            }
        });

        debug.log("loadAll", "Finished loading quarries");
    }

    public void remove(Quarry q) {
        quarries.remove(q);
        debug.log("remove", "Removed quarry owner=" + q.getOwner() +
                " controller=" + q.getController());
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
}
