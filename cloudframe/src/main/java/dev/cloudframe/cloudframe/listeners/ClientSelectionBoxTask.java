package dev.cloudframe.cloudframe.listeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugFlags;
import dev.cloudframe.cloudframe.util.DebugManager;

/**
 * Keeps a client-side BARRIER block spoofed at tube/controller blockspaces.
 *
 * Goal: full-block client collision for entity-only blocks without placing
 * real blocks server-side and without overriding vanilla block models.
 */
public final class ClientSelectionBoxTask {

    private static final Debug debug = DebugManager.get(ClientSelectionBoxTask.class);

    private static BukkitTask task;

    private static final long PERIOD_TICKS = 1L;

    // How often to re-send spoofed block changes as a desync self-heal.
    // The client can drop sendBlockChange state after certain interactions/chunk refreshes.
    private static final int FULL_REFRESH_EVERY_TICKS = 5;

    // How long (in ticks) we keep an aggressive refresh burst after a player interacts.
    private static final int INTERACT_REFRESH_BURST_TICKS = 8;

    private static long tickCounter = 0;

    // How many chunks around the player we keep spoofed barriers for.
    // 3 => 7x7 chunks => 112x112 blocks square.
    private static final int COLLISION_RADIUS_CHUNKS = 3;

    private static final Map<UUID, Set<Location>> spoofedByPlayer = new HashMap<>();

    private static final Map<UUID, UUID> lastWorldByPlayer = new HashMap<>();

    private static final Map<UUID, Integer> refreshBurstRemaining = new HashMap<>();

    // Support memory: if the client drops collision for a moment, the server can see the
    // player fall through air before our per-tick enforcement runs. Remember the last
    // virtual support block briefly so we can snap them back onto the top plane.
    private static final int SUPPORT_MEMORY_TICKS = 12;
    private static final Map<UUID, SupportRef> lastSupport = new HashMap<>();

    private record SupportRef(UUID worldId, int x, int y, int z, long tick) {}

    private record WorldChunk(UUID worldId, int cx, int cz) {}

    // Server-side safety: if we let clients collide with spoofed blocks, the server still
    // sees air and can kick players for "floating too long". While a player is standing
    // on a virtual tube/controller blockspace, temporarily grant allowFlight to bypass
    // the kick, and cancel actual flight toggles via a listener.
    private static final Set<UUID> flightBypassPlayers = new HashSet<>();
    private static final Map<UUID, Boolean> originalAllowFlight = new HashMap<>();

    private static BlockData barrier;

    private ClientSelectionBoxTask() {}

    public static void start(JavaPlugin plugin) {
        if (task != null) return;

        barrier = Bukkit.createBlockData(Material.BARRIER);

        if (DebugFlags.PICKBLOCK_LOGGING) {
            debug.log("start", "Client selection spoof material=" + Material.BARRIER);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, ClientSelectionBoxTask::tick, 1L, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            cleanupFor(p);
        }

        spoofedByPlayer.clear();
        lastWorldByPlayer.clear();
        flightBypassPlayers.clear();
        originalAllowFlight.clear();
        barrier = null;
    }

    public static boolean isFlightBypassActive(Player player) {
        if (player == null) return false;
        return flightBypassPlayers.contains(player.getUniqueId());
    }

    public static boolean isSpoofedFor(Player player, Location blockLoc) {
        if (player == null || blockLoc == null || blockLoc.getWorld() == null) return false;
        Set<Location> set = spoofedByPlayer.get(player.getUniqueId());
        if (set == null || set.isEmpty()) return false;
        Location n = norm(blockLoc.getWorld(), blockLoc);
        return set.contains(n);
    }

    /**
     * Request a short, aggressive spoof refresh burst for this player.
     * This helps when the client drops prior sendBlockChange state after interactions.
     */
    public static void requestRefreshBurst(Player player) {
        if (player == null) return;
        refreshBurstRemaining.put(player.getUniqueId(), INTERACT_REFRESH_BURST_TICKS);
    }

    /**
     * Immediately re-send spoofed BARRIER blocks for the player's current desired set.
     * Useful when an interaction causes the client to drop prior sendBlockChange state.
     */
    public static void forceRefreshNow(Player player) {
        if (player == null || !player.isOnline()) return;
        if (barrier == null) return;

        Set<Location> desired = computeDesiredSpoofLocations(player);
        UUID pid = player.getUniqueId();
        Set<Location> current = spoofedByPlayer.computeIfAbsent(pid, k -> new HashSet<>());

        // Ensure state contains all desired locations, and re-send all of them.
        for (Location loc : desired) {
            current.add(loc);
            apply(player, loc);
        }
    }

    /**
     * Immediately re-send the client-side BARRIER spoof for a specific blockspace.
     * Used to avoid one-tick "drops" when the client refreshes chunk sections
     * (e.g., opening/closing controller GUIs).
     */
    public static void reassertSpoofNow(Player player, Location blockLoc) {
        if (player == null || !player.isOnline()) return;
        if (barrier == null) return;
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location n = norm(blockLoc.getWorld(), blockLoc);
        spoofedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(n);
        apply(player, n);
    }

    public static void cleanupFor(Player player) {
        if (player == null) return;
        clearAllSpoofedFor(player);
        clearFlightBypassFor(player);
        lastWorldByPlayer.remove(player.getUniqueId());
        refreshBurstRemaining.remove(player.getUniqueId());
        lastSupport.remove(player.getUniqueId());
    }

    private static void tick() {
        if (barrier == null) return;

        tickCounter++;

        // Cleanup state for players that are no longer online.
        spoofedByPlayer.keySet().removeIf(pid -> Bukkit.getPlayer(pid) == null);
        lastWorldByPlayer.keySet().removeIf(pid -> Bukkit.getPlayer(pid) == null);
        refreshBurstRemaining.keySet().removeIf(pid -> Bukkit.getPlayer(pid) == null);
        flightBypassPlayers.removeIf(pid -> Bukkit.getPlayer(pid) == null);
        originalAllowFlight.keySet().removeIf(pid -> Bukkit.getPlayer(pid) == null);
        lastSupport.keySet().removeIf(pid -> Bukkit.getPlayer(pid) == null);

        // Track chunks we care about for entity support (same radius as spoof).
        final Set<WorldChunk> chunksToScan = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            // If the player changes worlds, clear any old spoofed locations first.
            UUID pid = player.getUniqueId();
            UUID wid = player.getWorld() != null ? player.getWorld().getUID() : null;
            UUID prevWid = lastWorldByPlayer.get(pid);
            if (prevWid != null && wid != null && !prevWid.equals(wid)) {
                cleanupFor(player);
            }
            if (wid != null) {
                lastWorldByPlayer.put(pid, wid);
            }

            updateCollisionSpoofFor(player);
            updateFlightBypassFor(player);

            // Server-authoritative backstop: even if the client drops collision for a frame,
            // keep the player from ever entering a virtual tube/controller blockspace.
            enforceVirtualCollisionFor(player);

            // Build chunk list for item support.
            Location pl = player.getLocation();
            World w = pl.getWorld();
            if (w != null) {
                int pcx = pl.getBlockX() >> 4;
                int pcz = pl.getBlockZ() >> 4;
                for (int dx = -COLLISION_RADIUS_CHUNKS; dx <= COLLISION_RADIUS_CHUNKS; dx++) {
                    for (int dz = -COLLISION_RADIUS_CHUNKS; dz <= COLLISION_RADIUS_CHUNKS; dz++) {
                        int cx = pcx + dx;
                        int cz = pcz + dz;
                        if (!w.isChunkLoaded(cx, cz)) continue;
                        chunksToScan.add(new WorldChunk(w.getUID(), cx, cz));
                    }
                }
            }
        }

        // Server-side support for dropped items: make them rest on virtual blocks.
        if (!chunksToScan.isEmpty()) {
            supportDroppedItemsInChunks(chunksToScan);
        }
    }

    private static void supportDroppedItemsInChunks(Set<WorldChunk> chunksToScan) {
        try {
            for (WorldChunk key : chunksToScan) {
                World w = Bukkit.getWorld(key.worldId());
                if (w == null) continue;
                if (!w.isChunkLoaded(key.cx(), key.cz())) continue;

                Chunk chunk = w.getChunkAt(key.cx(), key.cz());
                for (Entity ent : chunk.getEntities()) {
                    if (!(ent instanceof Item item)) continue;
                    if (!item.isValid() || item.isDead()) continue;

                    // Only help items that are falling or at rest.
                    if (item.getVelocity().getY() > 0.15) continue;

                    snapItemOntoVirtualSupport(item);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    private static void snapItemOntoVirtualSupport(Item item) {
        try {
            Location l = item.getLocation();
            World w = l.getWorld();
            if (w == null) return;

            int x = l.getBlockX();
            int z = l.getBlockZ();

            // Search a small vertical band around the item to find a virtual blockspace.
            // This allows correcting the common "drops to the real ground" case without
            // teleporting items up onto far-away floating tubes.
            int baseY = (int) Math.floor(l.getY());
            int topSearch = baseY + 3;
            int bottomSearch = baseY - 3;

            Integer supportY = null;
            for (int y = topSearch; y >= bottomSearch; y--) {
                if (!isVirtualBlockspaceGlobal(w, x, y, z)) continue;
                if (!w.getBlockAt(x, y, z).getType().isAir()) continue;
                supportY = y;
                break;
            }

            if (supportY == null) return;

            double topY = supportY + 1.0;
            double dy = topY - l.getY();

            // Only snap if the item is close enough to have plausibly fallen through.
            if (dy < -0.25) return; // item is already above the surface
            if (dy > 2.6) return;   // avoid pulling items up onto distant floating blocks

            Location fixed = l.clone();
            fixed.setY(topY + 0.05);
            item.teleport(fixed);
            item.setVelocity(new Vector(item.getVelocity().getX(), 0.0, item.getVelocity().getZ()));
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    private static boolean isVirtualBlockspaceGlobal(World world, int x, int y, int z) {
        if (world == null) return false;
        Location loc = new Location(world, x, y, z);
        if (CloudFrameRegistry.tubes() != null && CloudFrameRegistry.tubes().getTube(loc) != null) return true;
        return CloudFrameRegistry.quarries() != null && CloudFrameRegistry.quarries().hasControllerAt(loc);
    }

    private static void enforceVirtualCollisionFor(Player player) {
        try {
            GameMode gm = player.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;

            World world = player.getWorld();
            if (world == null) return;

            BoundingBox bb = player.getBoundingBox();

            // Update support memory when we detect a virtual support block directly beneath.
            updateSupportMemory(player, bb);

            // Robust support: if the player fell through client-side collision, they may no longer
            // overlap the virtual blockspace. Scan downward under their footprint and snap to the
            // highest virtual tube/controller we find.
            Integer scanSupportY = findHighestVirtualSupportUnder(player, bb, 10);
            if (scanSupportY != null) {
                double topY = scanSupportY + 1.0;
                Location loc = player.getLocation();
                if (loc.getY() < topY - 0.001) {
                    Location fixed = loc.clone();
                    fixed.setY(topY);
                    player.teleport(fixed);
                    player.setVelocity(new Vector(0, Math.max(0, player.getVelocity().getY()), 0));
                    player.setFallDistance(0.0f);
                } else {
                    player.setFallDistance(0.0f);
                }
                return;
            }

            // Scan virtual blocks overlapped by the player's current bounding box.
            final double eps = 1.0E-4;
            int minX = (int) Math.floor(bb.getMinX() + eps);
            int maxX = (int) Math.floor(bb.getMaxX() - eps);
            int minY = (int) Math.floor(bb.getMinY() + eps);
            int maxY = (int) Math.floor(bb.getMaxY() - eps);
            int minZ = (int) Math.floor(bb.getMinZ() + eps);
            int maxZ = (int) Math.floor(bb.getMaxZ() - eps);

            int bestSupportY = Integer.MIN_VALUE;
            boolean inside = false;

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!isVirtualBlockspaceFor(player, world, x, y, z)) continue;
                        if (!world.getBlockAt(x, y, z).getType().isAir()) continue;

                        inside = true;
                        bestSupportY = Math.max(bestSupportY, y);
                    }
                }
            }

            if (!inside) {
                // Also handle the common "fell just below the top plane" case.
                int underY = (int) Math.floor(bb.getMinY() - 0.001);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!isVirtualBlockspaceFor(player, world, x, underY, z)) continue;
                        if (!world.getBlockAt(x, underY, z).getType().isAir()) continue;
                        double topY = underY + 1.0;
                        if (bb.getMinY() < topY + 0.08) {
                            bestSupportY = Math.max(bestSupportY, underY);
                            inside = true;
                        }
                    }
                }
            }

            if (!inside || bestSupportY == Integer.MIN_VALUE) {
                // If we are no longer overlapping the virtual blockspace, we may still need to
                // snap back onto the last known support (spoof blip can let the server move us
                // below the block before we run).
                SupportRef ref = lastSupport.get(player.getUniqueId());
                if (ref == null) return;
                if (tickCounter - ref.tick() > SUPPORT_MEMORY_TICKS) return;
                if (world.getUID() == null || !world.getUID().equals(ref.worldId())) return;

                // Only snap if player is still over that support column.
                Location l = player.getLocation();
                if (!isFootprintOverBlock(bb, ref.x(), ref.z())) return;

                double topY = ref.y() + 1.0;
                if (l.getY() < topY - 0.001) {
                    Location fixed = l.clone();
                    fixed.setY(topY);
                    player.teleport(fixed);
                    player.setVelocity(new Vector(0, Math.max(0, player.getVelocity().getY()), 0));
                    player.setFallDistance(0.0f);
                }
                return;
            }

            double topY = bestSupportY + 1.0;
            Location loc = player.getLocation();
            if (loc.getY() < topY) {
                Location fixed = loc.clone();
                fixed.setY(topY);
                player.teleport(fixed);
                player.setVelocity(new Vector(0, Math.max(0, player.getVelocity().getY()), 0));
                player.setFallDistance(0.0f);
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    private static void updateSupportMemory(Player player, BoundingBox bb) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            // Sample just below feet.
            int by = (int) Math.floor(bb.getMinY() - 0.01);

            int minX = (int) Math.floor(bb.getMinX() + 1.0E-4);
            int maxX = (int) Math.floor(bb.getMaxX() - 1.0E-4);
            int minZ = (int) Math.floor(bb.getMinZ() + 1.0E-4);
            int maxZ = (int) Math.floor(bb.getMaxZ() - 1.0E-4);

            int bestY = Integer.MIN_VALUE;
            int bestX = 0;
            int bestZ = 0;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isVirtualBlockspaceFor(player, world, x, by, z)) continue;
                    if (!world.getBlockAt(x, by, z).getType().isAir()) continue;
                    if (by > bestY) {
                        bestY = by;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }

            if (bestY != Integer.MIN_VALUE) {
                lastSupport.put(player.getUniqueId(), new SupportRef(world.getUID(), bestX, bestY, bestZ, tickCounter));
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    private static Integer findHighestVirtualSupportUnder(Player player, BoundingBox bb, int scanDepthBlocks) {
        World world = player.getWorld();
        if (world == null) return null;

        final double eps = 1.0E-4;
        int minX = (int) Math.floor(bb.getMinX() + eps);
        int maxX = (int) Math.floor(bb.getMaxX() - eps);
        int minZ = (int) Math.floor(bb.getMinZ() + eps);
        int maxZ = (int) Math.floor(bb.getMaxZ() - eps);

        int startY = (int) Math.floor(bb.getMinY() + eps);
        int bottomY = startY - Math.max(1, scanDepthBlocks);

        int best = Integer.MIN_VALUE;

        for (int y = startY; y >= bottomY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isVirtualBlockspaceFor(player, world, x, y, z)) continue;
                    if (!world.getBlockAt(x, y, z).getType().isAir()) continue;
                    best = y;
                    // We scan from top down; first hit is the highest.
                    return best;
                }
            }
        }

        return null;
    }

    private static boolean isFootprintOverBlock(BoundingBox bb, int bx, int bz) {
        if (bb == null) return false;
        double minX = bb.getMinX();
        double maxX = bb.getMaxX();
        double minZ = bb.getMinZ();
        double maxZ = bb.getMaxZ();

        // Block column bounds.
        double bminX = bx;
        double bmaxX = bx + 1.0;
        double bminZ = bz;
        double bmaxZ = bz + 1.0;

        boolean xOverlap = maxX > bminX + 1.0E-6 && minX < bmaxX - 1.0E-6;
        boolean zOverlap = maxZ > bminZ + 1.0E-6 && minZ < bmaxZ - 1.0E-6;
        return xOverlap && zOverlap;
    }

    private static boolean isVirtualBlockspaceFor(Player player, World world, int x, int y, int z) {
        if (world == null) return false;

        // Primary: use the per-player spoof set (ground truth for what the client is colliding with).
        if (player != null) {
            Set<Location> set = spoofedByPlayer.get(player.getUniqueId());
            if (set != null && !set.isEmpty()) {
                if (set.contains(new Location(world, x, y, z))) return true;
            }
        }

        // Fallback: registry lookups.
        Location loc = new Location(world, x, y, z);
        if (CloudFrameRegistry.tubes() != null && CloudFrameRegistry.tubes().getTube(loc) != null) return true;
        return CloudFrameRegistry.quarries() != null && CloudFrameRegistry.quarries().hasControllerAt(loc);
    }

    private static void updateFlightBypassFor(Player player) {
        GameMode gm = player.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) {
            // Don't touch creative/spectator (they manage flight themselves).
            clearFlightBypassFor(player);
            return;
        }

        Integer supportBlockYBoxed = findVirtualSupportBlockY(player);
        UUID pid = player.getUniqueId();

        if (supportBlockYBoxed != null) {
            int supportBlockY = supportBlockYBoxed.intValue();

            // Only grant allowFlight if we are the one enabling it.
            if (!flightBypassPlayers.contains(pid) && !player.getAllowFlight()) {
                originalAllowFlight.put(pid, player.getAllowFlight());
                player.setAllowFlight(true);
                player.setFlying(false);
                flightBypassPlayers.add(pid);
            }

            // Server-side "virtual floor": if the client briefly drops spoofed collision,
            // keep the player from falling through by snapping them back onto the top surface.
            applyVirtualFloorSupport(player, supportBlockY);
            return;
        }

        // Not standing on virtual support anymore: restore original state.
        clearFlightBypassFor(player);
    }

    private static void clearFlightBypassFor(Player player) {
        UUID pid = player.getUniqueId();
        if (!flightBypassPlayers.remove(pid)) return;

        Boolean original = originalAllowFlight.remove(pid);
        // Only restore if we had previously recorded a value.
        if (original != null) {
            try {
                player.setFlying(false);
            } catch (Throwable ignored) {
                // Best-effort.
            }
            player.setAllowFlight(original);
        }
    }

    private static Integer findVirtualSupportBlockY(Player player) {
        World world = player.getWorld();
        if (world == null) return null;

        // Check the block(s) around the player's feet.
        BoundingBox bb = player.getBoundingBox();
        double yBelow = bb.getMinY() - 0.01;
        int by = (int) Math.floor(yBelow);

        int minX = (int) Math.floor(bb.getMinX() + 1.0E-4);
        int maxX = (int) Math.floor(bb.getMaxX() - 1.0E-4);
        int minZ = (int) Math.floor(bb.getMinZ() + 1.0E-4);
        int maxZ = (int) Math.floor(bb.getMaxZ() - 1.0E-4);

        // When the client drops collision for a frame, the player can dip into the blockspace
        // and/or fall fast enough that the sampled Y changes between ticks.
        // Scan a small vertical range and pick the highest support block we find.
        int bestY = Integer.MIN_VALUE;

        int yTop = by + 1;
        int yBottom = by - 3;

        for (int y = yTop; y >= yBottom; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isVirtualBlockspaceFor(player, world, x, y, z)) {
                        bestY = Math.max(bestY, y);
                        break;
                    }
                }
                if (bestY == y) {
                    // Found support at this Y; because we're scanning top-down, we can return immediately.
                    return bestY;
                }
            }
        }

        return null;
    }

    private static void applyVirtualFloorSupport(Player player, int supportBlockY) {
        try {
            double topY = supportBlockY + 1.0;
            double y = player.getLocation().getY();

            // If they've dipped below the top surface by even a tiny amount, snap them back.
            if (y < topY - 0.001) {
                var l = player.getLocation();
                l.setY(topY);
                player.teleport(l);
                player.setFallDistance(0.0f);
            } else {
                player.setFallDistance(0.0f);
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    /**
     * Keep client-side BARRIER blocks spoofed at all nearby tube/controller
     * blockspaces so players collide with them like full blocks.
     */
    private static void updateCollisionSpoofFor(Player player) {
        try {
            Set<Location> desired = computeDesiredSpoofLocations(player);
            UUID pid = player.getUniqueId();
            Set<Location> current = spoofedByPlayer.computeIfAbsent(pid, k -> new HashSet<>());

            // Remove locations no longer desired.
            for (Iterator<Location> it = current.iterator(); it.hasNext();) {
                Location loc = it.next();
                if (!desired.contains(loc)) {
                    // Important: remove from spoof set before restoring.
                    // Otherwise the ProtocolLib interceptor will rewrite our AIR restore back to BARRIER.
                    it.remove();
                    restore(player, loc);
                }
            }

            // Add new desired locations.
            for (Location loc : desired) {
                if (current.add(loc)) {
                    apply(player, loc);
                }
            }

        // Desync self-heal:
        // The client can drop sendBlockChange spoof state (tubes + controllers) after certain
        // interactions or chunk refreshes. Periodically re-send, and do an aggressive burst
        // right after player interactions.
            boolean doPeriodicRefresh = (tickCounter % FULL_REFRESH_EVERY_TICKS) == 0;
            int burst = refreshBurstRemaining.getOrDefault(pid, 0);
            boolean doBurstRefresh = burst > 0;
            if (doBurstRefresh) {
                if (burst <= 1) refreshBurstRemaining.remove(pid);
                else refreshBurstRemaining.put(pid, burst - 1);
            }

            if (doPeriodicRefresh || doBurstRefresh) {
                for (Location loc : desired) {
                    apply(player, loc);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort; never let this task die.
        }
    }

    private static Set<Location> computeDesiredSpoofLocations(Player player) {
        Location pl = player.getLocation();
        World world = pl.getWorld();
        if (world == null) return Set.of();

        int pcx = pl.getBlockX() >> 4;
        int pcz = pl.getBlockZ() >> 4;

        Set<Location> out = new HashSet<>();

        for (int dx = -COLLISION_RADIUS_CHUNKS; dx <= COLLISION_RADIUS_CHUNKS; dx++) {
            for (int dz = -COLLISION_RADIUS_CHUNKS; dz <= COLLISION_RADIUS_CHUNKS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!world.isChunkLoaded(cx, cz)) continue;

                Chunk chunk = world.getChunkAt(cx, cz);

                if (CloudFrameRegistry.tubes() != null) {
                    for (Location loc : CloudFrameRegistry.tubes().tubeLocationsInChunk(chunk)) {
                        if (loc == null || loc.getWorld() == null) continue;
                        if (!loc.getWorld().equals(world)) continue;
                        Location n = norm(world, loc);
                        if (n.getBlock().getType().isAir()) out.add(n);
                    }
                }

                if (CloudFrameRegistry.quarries() != null) {
                    for (Location loc : CloudFrameRegistry.quarries().controllerLocationsInChunk(chunk)) {
                        if (loc == null || loc.getWorld() == null) continue;
                        if (!loc.getWorld().equals(world)) continue;
                        Location n = norm(world, loc);
                        if (n.getBlock().getType().isAir()) out.add(n);
                    }
                }
            }
        }

        return out;
    }

    private static Location norm(World world, Location loc) {
        return new Location(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static void clearAllSpoofedFor(Player player) {
        Set<Location> set = spoofedByPlayer.remove(player.getUniqueId());
        if (set == null || set.isEmpty()) return;
        for (Location loc : set) {
            restore(player, loc);
        }
        set.clear();
    }

    private static void apply(Player player, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;
        player.sendBlockChange(blockLoc, barrier);
    }

    private static void restore(Player player, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;
        player.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
    }
}