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
    private static final double RAY_SIZE = 0.45;
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

        // Primary: raytrace tube/controller Interaction entities (cheap and accurate when it hits).
        RayTraceResult rr = player.getWorld().rayTraceEntities(
            eye,
            dir,
            limit,
            RAY_SIZE,
            ClientSelectionBoxTask::isHighlightableEntity
        );

        if (rr != null && rr.getHitEntity() != null) {
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
            if (controllerLoc != null) return controllerLoc;
        }

        // Fallback: scan blockspaces along the player's crosshair ray to find the nearest
        // entity-only "block" location (tube/controller). This avoids outline flicker when
        // the Interaction entity raytrace misses at certain angles.
        return scanVirtualBlockspacesAlongRay(eye, dir, limit);
    }

    private static Location scanVirtualBlockspacesAlongRay(Location eye, Vector dir, double limit) {
        if (eye == null || eye.getWorld() == null || dir == null) return null;

        final double step = 0.10; // smaller step reduces misses on thin geometry / grazing angles
        final double maxDistSqToAabb = 0.35 * 0.35; // how close the ray must be to the blockspace

        Location sample = eye.clone();
        for (double d = 0.0; d <= limit; d += step) {
            sample.set(
                eye.getX() + dir.getX() * d,
                eye.getY() + dir.getY() * d,
                eye.getZ() + dir.getZ() * d
            );

            int bx = sample.getBlockX();
            int by = sample.getBlockY();
            int bz = sample.getBlockZ();

            Location best = findNearestVirtualAtOrNear(eye, sample.toVector(), bx, by, bz, maxDistSqToAabb);
            if (best != null) return best;
        }

        return null;
    }

    private static Location findNearestVirtualAtOrNear(Location eye, Vector sample, int bx, int by, int bz, double maxDistSqToAabb) {
        if (eye == null || eye.getWorld() == null) return null;

        // Check the sampled block plus immediate neighbors. This makes selection robust
        // when the crosshair ray grazes a tube's blockspace (common at junctions).
        int[][] offsets = new int[][] {
            {0, 0, 0},
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
        };

        Location bestLoc = null;
        double bestDistSq = Double.POSITIVE_INFINITY;

        for (int[] o : offsets) {
            int x = bx + o[0];
            int y = by + o[1];
            int z = bz + o[2];

            Location cand = new Location(eye.getWorld(), x, y, z);
            boolean isTube = CloudFrameRegistry.tubes().getTube(cand) != null;
            boolean isController = !isTube && CloudFrameRegistry.quarries().hasControllerAt(cand);
            if (!isTube && !isController) continue;

            double distSq = distSqPointToUnitAabb(sample.getX(), sample.getY(), sample.getZ(), x, y, z);
            if (distSq <= maxDistSqToAabb && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestLoc = cand;
            }
        }

        return bestLoc;
    }

    private static double distSqPointToUnitAabb(double px, double py, double pz, int ax, int ay, int az) {
        double dx = 0.0;
        if (px < ax) dx = ax - px;
        else if (px > ax + 1.0) dx = px - (ax + 1.0);

        double dy = 0.0;
        if (py < ay) dy = ay - py;
        else if (py > ay + 1.0) dy = py - (ay + 1.0);

        double dz = 0.0;
        if (pz < az) dz = az - pz;
        else if (pz > az + 1.0) dz = pz - (az + 1.0);

        return dx * dx + dy * dy + dz * dz;
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
