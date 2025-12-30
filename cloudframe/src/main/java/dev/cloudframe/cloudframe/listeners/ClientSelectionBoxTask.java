package dev.cloudframe.cloudframe.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.FluidCollisionMode;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;

/**
 * Creates a vanilla-style selection box for entity-only "blocks" by sending a
 * client-side block change at the tube/controller block position.
 *
 * This uses an invisible block (BARRIER) so the client renders the normal block
 * selection outline without particles/glow.
 */
public final class ClientSelectionBoxTask {

    private static BukkitTask task;

    private static final double MAX_DISTANCE = 6.0;
    // Keep this small; large radii can hit neighboring tube Interactions and select the wrong block.
    private static final double RAY_SIZE = 0.12;
    private static final long PERIOD_TICKS = 1L;

    // Raytracing entities can briefly miss when aiming near edges; debounce clears to avoid flicker.
    private static final int CLEAR_AFTER_MISSES = 8;

    private static final Map<UUID, HighlightState> stateByPlayer = new HashMap<>();

    private static final class HighlightState {
        Location loc;
        int misses;

        HighlightState(Location loc) {
            this.loc = loc;
            this.misses = 0;
        }
    }

    private static BlockData selectionBlock;

    private ClientSelectionBoxTask() {}

    public static void start(JavaPlugin plugin) {
        if (task != null) return;

        // Barrier is invisible but still gets a vanilla selection box.
        selectionBlock = Bukkit.createBlockData(Material.BARRIER);

        task = Bukkit.getScheduler().runTaskTimer(plugin, ClientSelectionBoxTask::tick, 1L, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        // Best-effort: restore real blocks for online players.
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearFor(p);
        }

        stateByPlayer.clear();
        selectionBlock = null;
    }

    private static void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            Location base = findTargetBase(player);
            updateFor(player, base);
        }
    }

    private static void updateFor(Player player, Location baseBlockLoc) {
        UUID pid = player.getUniqueId();
        HighlightState state = stateByPlayer.get(pid);
        Location prev = state != null ? state.loc : null;

        if (baseBlockLoc == null) {
            if (state != null && prev != null) {
                state.misses++;
                if (state.misses >= CLEAR_AFTER_MISSES) {
                    restore(player, prev);
                    stateByPlayer.remove(pid);
                }
            }
            return;
        }

        // Only place a client-side selection block if the world is actually air.
        // (These entity-only blocks should always live in air.)
        if (!baseBlockLoc.getBlock().getType().isAir()) {
            if (state != null && prev != null) {
                state.misses++;
                if (state.misses >= CLEAR_AFTER_MISSES) {
                    restore(player, prev);
                    stateByPlayer.remove(pid);
                }
            }
            return;
        }

        if (prev != null && sameBlock(prev, baseBlockLoc)) {
            // Keep current.
            if (state != null) state.misses = 0;
            return;
        }

        if (prev != null) {
            restore(player, prev);
        }

        apply(player, baseBlockLoc);
        if (state == null) {
            stateByPlayer.put(pid, new HighlightState(baseBlockLoc));
        } else {
            state.loc = baseBlockLoc;
            state.misses = 0;
        }
    }

    private static void clearFor(Player player) {
        HighlightState state = stateByPlayer.remove(player.getUniqueId());
        if (state == null || state.loc == null) return;
        restore(player, state.loc);
    }

    private static void apply(Player player, Location blockLoc) {
        if (selectionBlock == null) return;
        player.sendBlockChange(blockLoc, selectionBlock);
    }

    private static void restore(Player player, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;
        player.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
    }

    private static Location findTargetBase(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        // Don't highlight through solid blocks; limit to first real block hit like vanilla.
        double limit = MAX_DISTANCE;
        RayTraceResult br = player.getWorld().rayTraceBlocks(eye, dir, MAX_DISTANCE, FluidCollisionMode.NEVER, true);
        if (br != null && br.getHitPosition() != null) {
            limit = eye.toVector().distance(br.getHitPosition());
            limit = Math.max(0.0, limit - 0.01);
        }

        // Primary: voxel ray-walk (like vanilla block targeting). This reliably selects the
        // actual blockspace under the crosshair and avoids accidentally selecting a neighboring
        // tube/controller due to a fat entity ray.
        Location hitVirtual = findFirstVirtualBlockspaceAlongRay(eye, dir, limit);
        if (hitVirtual != null) return hitVirtual;

        // Fallback: raytrace tube/controller Interaction entities.
        RayTraceResult rr = player.getWorld().rayTraceEntities(
            eye,
            dir,
            limit,
            RAY_SIZE,
            ClientSelectionBoxTask::isHighlightableEntity
        );

        if (rr == null || rr.getHitEntity() == null) return null;

        Entity hit = rr.getHitEntity();
        PersistentDataContainer pdc = hit.getPersistentDataContainer();

        Location tubeLoc = null;
        if (CloudFrameRegistry.tubes().visualsManager() != null) {
            tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc);
        }
        if (tubeLoc != null) return tubeLoc;

        Location controllerLoc = null;
        if (CloudFrameRegistry.quarries().visualsManager() != null) {
            controllerLoc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc);
        }
        return controllerLoc;
    }

    /**
     * Walk the voxel grid along the ray (like vanilla block targeting) and return
     * the first tube/controller blockspace encountered.
     */
    private static Location findFirstVirtualBlockspaceAlongRay(Location eye, Vector dir, double limit) {
        if (eye == null || eye.getWorld() == null || dir == null) return null;
        if (limit <= 0.0) return null;

        Vector d = dir.clone();
        double len = d.length();
        if (len == 0.0) return null;
        d.multiply(1.0 / len);

        // Start slightly forward so we don't select inside the player's head.
        double ox = eye.getX() + d.getX() * 0.01;
        double oy = eye.getY() + d.getY() * 0.01;
        double oz = eye.getZ() + d.getZ() * 0.01;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = d.getX() > 0 ? 1 : (d.getX() < 0 ? -1 : 0);
        int stepY = d.getY() > 0 ? 1 : (d.getY() < 0 ? -1 : 0);
        int stepZ = d.getZ() > 0 ? 1 : (d.getZ() < 0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getX());
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getY());
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getZ());

        double nextVoxelBoundaryX = stepX > 0 ? (x + 1.0) : x;
        double nextVoxelBoundaryY = stepY > 0 ? (y + 1.0) : y;
        double nextVoxelBoundaryZ = stepZ > 0 ? (z + 1.0) : z;

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryX - ox) / d.getX();
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryY - oy) / d.getY();
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryZ - oz) / d.getZ();

        // Clamp negative due to numeric issues.
        if (tMaxX < 0) tMaxX = 0;
        if (tMaxY < 0) tMaxY = 0;
        if (tMaxZ < 0) tMaxZ = 0;

        double t = 0.0;
        while (t <= limit) {
            Location blockLoc = new Location(eye.getWorld(), x, y, z);
            if (CloudFrameRegistry.tubes().getTube(blockLoc) != null) return blockLoc;
            if (CloudFrameRegistry.quarries().hasControllerAt(blockLoc)) return blockLoc;

            // Step to next voxel.
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxX && tMaxY <= tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }

        return null;
    }

    private static boolean isHighlightableEntity(Entity e) {
        if (e == null) return false;
        if (!(e instanceof Interaction)) return false;

        PersistentDataContainer pdc = e.getPersistentDataContainer();

        if (CloudFrameRegistry.tubes().visualsManager() != null && CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc) != null) {
            return true;
        }
        if (CloudFrameRegistry.quarries().visualsManager() != null && CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc) != null) {
            return true;
        }

        return false;
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
