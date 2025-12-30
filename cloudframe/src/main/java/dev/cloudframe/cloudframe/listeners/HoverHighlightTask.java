package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
public final class HoverHighlightTask {

    private static BukkitTask task;

    private static final double MAX_DISTANCE = 5.0;
    private static final long PERIOD_TICKS = 5L;

    private static final DustOptions OUTLINE_DUST = new DustOptions(Color.fromRGB(255, 255, 255), 0.45f);

    private HoverHighlightTask() {}

    public static void start(JavaPlugin plugin) {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, HoverHighlightTask::tick, 1L, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection();

            RayTraceResult rr = player.getWorld().rayTraceEntities(
                eye,
                dir,
                MAX_DISTANCE,
                0.2,
                HoverHighlightTask::isHighlightableEntity
            );

            if (rr == null) continue;
            Entity hit = rr.getHitEntity();
            if (hit == null) continue;

            Location tubeLoc = null;
            if (CloudFrameRegistry.tubes().visualsManager() != null) {
                tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(hit.getPersistentDataContainer());
            }

            if (tubeLoc != null) {
                // Highlight the tube itself.
                spawnWireframeOutline(player, tubeLoc);
                continue;
            }

            Location controllerLoc = null;
            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                controllerLoc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(hit.getPersistentDataContainer());
            }
            if (controllerLoc != null) {
                spawnWireframeOutline(player, controllerLoc);
            }
        }
    }

    private static boolean isHighlightableEntity(Entity e) {
        if (e == null) return false;
        var pdc = e.getPersistentDataContainer();
        if (CloudFrameRegistry.tubes().visualsManager() != null && CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc) != null) {
            return true;
        }
        if (CloudFrameRegistry.quarries().visualsManager() != null && CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc) != null) {
            return true;
        }
        return false;
    }

    private static void spawnWireframeOutline(Player player, Location blockLoc) {
        var world = blockLoc.getWorld();
        if (world == null) return;

        double x = blockLoc.getBlockX();
        double y = blockLoc.getBlockY();
        double z = blockLoc.getBlockZ();

        // Full wireframe (12 edges), slight inset to reduce z-fighting with block faces.
        double inset = 0.02;
        double x0 = x + inset;
        double x1 = x + 1 - inset;
        double y0 = y + inset;
        double y1 = y + 1 - inset;
        double z0 = z + inset;
        double z1 = z + 1 - inset;

        // Edge density: particles per edge.
        int steps = 4;

        // Bottom rectangle
        spawnEdge(player, x0, y0, z0, x1, y0, z0, steps);
        spawnEdge(player, x1, y0, z0, x1, y0, z1, steps);
        spawnEdge(player, x1, y0, z1, x0, y0, z1, steps);
        spawnEdge(player, x0, y0, z1, x0, y0, z0, steps);

        // Top rectangle
        spawnEdge(player, x0, y1, z0, x1, y1, z0, steps);
        spawnEdge(player, x1, y1, z0, x1, y1, z1, steps);
        spawnEdge(player, x1, y1, z1, x0, y1, z1, steps);
        spawnEdge(player, x0, y1, z1, x0, y1, z0, steps);

        // Vertical edges
        spawnEdge(player, x0, y0, z0, x0, y1, z0, steps);
        spawnEdge(player, x1, y0, z0, x1, y1, z0, steps);
        spawnEdge(player, x1, y0, z1, x1, y1, z1, steps);
        spawnEdge(player, x0, y0, z1, x0, y1, z1, steps);
    }

    private static void spawnEdge(Player player, double ax, double ay, double az, double bx, double by, double bz, int steps) {
        if (steps < 2) steps = 2;
        double dx = (bx - ax) / (steps - 1.0);
        double dy = (by - ay) / (steps - 1.0);
        double dz = (bz - az) / (steps - 1.0);
        for (int i = 0; i < steps; i++) {
            double x = ax + dx * i;
            double y = ay + dy * i;
            double z = az + dz * i;
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, OUTLINE_DUST);
        }
    }
}
