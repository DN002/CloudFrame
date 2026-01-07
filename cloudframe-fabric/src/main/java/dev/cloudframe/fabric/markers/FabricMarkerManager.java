package dev.cloudframe.fabric.markers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.cloudframe.common.markers.MarkerPos;
import dev.cloudframe.common.markers.MarkerSelectionService;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Manages 2D marker placement on Fabric.
 * Players place 4 corner markers on the same Y plane to define a rectangular frame region.
 * All 4 corners must share the same Y coordinate.
 */
public class FabricMarkerManager {

    private static final Debug debug = DebugManager.get(FabricMarkerManager.class);

    private final MarkerSelectionService service;

    public FabricMarkerManager(MarkerSelectionService service) {
        this.service = service;
    }

    /**
     * Add a corner marker. Returns the corner number (1-4) or -1 if validation failed.
     */
    public int addCorner(ServerPlayerEntity player, BlockPos pos) {
        UUID id = player.getUuid();
        String worldId = "minecraft:overworld";
        try {
            worldId = player.getCommandSource().getWorld().getRegistryKey().getValue().toString();
        } catch (Throwable ignored) {
            worldId = "minecraft:overworld";
        }

        int cornerNum = service.addCorner(id, worldId, pos.getX(), pos.getY(), pos.getZ());
        if (cornerNum == -1) {
            debug.log("addCorner", "Corner rejected/reset for player " + id);
        } else {
            debug.log("addCorner", "Added corner " + cornerNum + " at " + pos + " for player " + id);
        }
        return cornerNum;
    }

    /**
     * Clear markers for a player (left-click).
     */
    public void clearCorners(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean existed = service.get(id) != null;
        service.clearCorners(id);
        debug.log("clearCorners", "Cleared markers for player " + id + " (existed=" + existed + ")");
    }

    /**
     * Get current corners for a player.
     */
    public List<BlockPos> getCorners(ServerPlayerEntity player) {
        List<MarkerPos> corners = service.getCorners(player.getUuid());
        List<BlockPos> result = new ArrayList<>(corners.size());
        for (MarkerPos c : corners) {
            if (c == null) continue;
            result.add(new BlockPos(c.x(), c.y(), c.z()));
        }
        return result;
    }

    /**
     * Check if player has all 4 corners set.
     */
    public boolean isComplete(ServerPlayerEntity player) {
        return service.isComplete(player.getUuid());
    }

    /**
     * Check if player's frame is activated (wrench confirmed).
     */
    public boolean isActivated(UUID playerId) {
        return service.isActivated(playerId);
    }

    /**
     * Activate the frame (wrench confirmation).
     */
    public void activateFrame(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (!service.isComplete(id)) return;
        service.activateFrame(id);
        debug.log("activateFrame", "Frame activated for player " + id);
    }

    /**
     * Store frame from found marker blocks (wrench scan).
     */
    public void setFrameFromBlocks(UUID playerId, ServerWorld world, List<BlockPos> foundMarkers) {
        if (foundMarkers == null || foundMarkers.size() != 4) return;

        // Dimension-aware: frames are scoped to the world they were created in.
        // If the world is unavailable, drop the selection.
        if (world == null) {
            service.clearCorners(playerId);
            return;
        }
        
        List<MarkerPos> corners = new ArrayList<>(4);
        for (BlockPos pos : foundMarkers) {
            if (pos == null) continue;
            corners.add(new MarkerPos(pos.getX(), pos.getY(), pos.getZ()));
        }

        String worldId = "minecraft:overworld";
        try {
            if (world != null) {
                worldId = world.getRegistryKey().getValue().toString();
            }
        } catch (Throwable ignored) {
            worldId = "minecraft:overworld";
        }

        service.setFrameFromCorners(playerId, worldId, corners, true, true);
        debug.log("setFrameFromBlocks", "Stored activated frame for player " + playerId + " from " + corners.size() + " markers");
    }

    /**
     * Called every server tick (or throttled by caller). Spawns persistent red frame particles
     * for activated frames and auto-deactivates frames if any marker block is missing.
     */
    public void tick(net.minecraft.server.MinecraftServer server, int tickCounter) {
        // Throttle particle spam.
        if (server == null) return;
        if (tickCounter % 5 != 0) return; // every 5 ticks

        // Iterate a snapshot to avoid CME if something clears during iteration.
        var snapshot = new java.util.ArrayList<>(service.snapshot().entrySet());

        for (var entry : snapshot) {
            UUID playerId = entry.getKey();
            var sel = entry.getValue();
            if (sel == null || !sel.activated() || sel.corners() == null || sel.corners().size() != 4) continue;

            RegistryKey<World> key = World.OVERWORLD;
            try {
                String worldId = sel.worldId();
                if (worldId != null && !worldId.isBlank()) {
                    key = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(worldId));
                }
            } catch (Throwable ignored) {
                key = World.OVERWORLD;
            }

            ServerWorld world = server.getWorld(key);
            if (world == null) continue;

            List<BlockPos> corners = new ArrayList<>(4);
            for (MarkerPos c : sel.corners()) {
                if (c == null) continue;
                corners.add(new BlockPos(c.x(), c.y(), c.z()));
            }

            // If any marker is gone, deactivate the frame.
            boolean allPresent = true;
            for (BlockPos corner : corners) {
                if (corner == null) continue;
                if (world.getBlockState(corner).getBlock() != dev.cloudframe.fabric.content.CloudFrameContent.MARKER_BLOCK) {
                    allPresent = false;
                    break;
                }
            }

            if (!allPresent) {
                service.clearCorners(playerId);
                debug.log("tick", "Deactivated frame for player " + playerId + " (marker missing)");
                continue;
            }

            spawnFrameLines(world, corners);
        }
    }

    /**
     * Deactivate any active frame that contains the given marker position.
     */
    public void onMarkerBroken(ServerWorld world, BlockPos pos) {
        if (pos == null) return;
        String worldId = "minecraft:overworld";
        try {
            if (world != null) {
                worldId = world.getRegistryKey().getValue().toString();
            }
        } catch (Throwable ignored) {
            worldId = "minecraft:overworld";
        }

        MarkerPos broken = new MarkerPos(pos.getX(), pos.getY(), pos.getZ());

        var snapshot = new java.util.ArrayList<>(service.snapshot().entrySet());
        for (var entry : snapshot) {
            UUID playerId = entry.getKey();
            var sel = entry.getValue();
            if (sel == null || !sel.activated() || sel.corners() == null) continue;
            if (sel.worldId() != null && !sel.worldId().equals(worldId)) continue;
            if (sel.containsCorner(broken)) {
                service.clearCorners(playerId);
                debug.log("onMarkerBroken", "Deactivated frame for player " + playerId + " (broken marker at " + pos + ")");
            }
        }
    }

    private static final int RED = 0xFF0000;

    private static void spawnFrameLines(ServerWorld world, List<BlockPos> corners) {
        if (corners == null || corners.size() < 4) return;

        BlockPos first = corners.get(0);
        int minX = first.getX();
        int maxX = first.getX();
        int minZ = first.getZ();
        int maxZ = first.getZ();
        int y = first.getY();

        for (BlockPos corner : corners) {
            if (corner == null) continue;
            minX = Math.min(minX, corner.getX());
            maxX = Math.max(maxX, corner.getX());
            minZ = Math.min(minZ, corner.getZ());
            maxZ = Math.max(maxZ, corner.getZ());
        }

        // Render on the marker perimeter so particles line up with the placed markers.

        BlockPos c1 = new BlockPos(minX, y, minZ);
        BlockPos c2 = new BlockPos(maxX, y, minZ);
        BlockPos c3 = new BlockPos(maxX, y, maxZ);
        BlockPos c4 = new BlockPos(minX, y, maxZ);

        spawnLine(world, c1, c2);
        spawnLine(world, c2, c3);
        spawnLine(world, c3, c4);
        spawnLine(world, c4, c1);
    }

    private static void spawnLine(ServerWorld world, BlockPos from, BlockPos to) {
        Vec3d start = Vec3d.ofCenter(from).add(0, 0.5, 0);
        Vec3d end = Vec3d.ofCenter(to).add(0, 0.5, 0);
        double distance = start.distanceTo(end);
        // Higher density so the perimeter reads like a continuous line.
        int particleCount = Math.max(8, (int) (distance * 4.0)); // ~4 per block
        // Slightly smaller dust so it looks like a thin laser line.
        DustParticleEffect dust = new DustParticleEffect(RED, 0.65f);

        for (int i = 0; i <= particleCount; i++) {
            double t = i / (double) particleCount;
            Vec3d pos = start.lerp(end, t);
            world.spawnParticles(dust, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Get corners for a player by UUID.
     */
    public List<BlockPos> getCorners(UUID playerId) {
        List<MarkerPos> corners = service.getCorners(playerId);
        List<BlockPos> result = new ArrayList<>(corners.size());
        for (MarkerPos c : corners) {
            if (c == null) continue;
            result.add(new BlockPos(c.x(), c.y(), c.z()));
        }
        return result;
    }

    /**
     * Clear markers for a player by UUID.
     */
    public void clearCorners(UUID playerId) {
        boolean existed = service.get(playerId) != null;
        service.clearCorners(playerId);
        debug.log("clearCorners", "Cleared markers for player " + playerId + " (existed=" + existed + ")");
    }

    public void loadAll() {
        service.loadAll();
    }

    public void saveAll() {
        service.saveAll();
    }
}
