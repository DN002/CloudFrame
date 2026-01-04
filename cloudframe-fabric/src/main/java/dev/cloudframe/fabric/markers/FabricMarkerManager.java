package dev.cloudframe.fabric.markers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.storage.Database;
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

    private record MarkerSelection(List<BlockPos> corners, int yLevel, boolean activated, RegistryKey<World> worldKey) {
        public MarkerSelection {
            if (corners == null) corners = new ArrayList<>();
        }
        
        // Convenience constructor without activated flag
        public MarkerSelection(List<BlockPos> corners, int yLevel) {
            this(corners, yLevel, false, World.OVERWORLD);
        }
    }

    private final Map<UUID, MarkerSelection> selections = new HashMap<>();

    /**
     * Add a corner marker. Returns the corner number (1-4) or -1 if validation failed.
     */
    public int addCorner(ServerPlayerEntity player, BlockPos pos) {
        UUID id = player.getUuid();
        MarkerSelection cur = selections.get(id);
        List<BlockPos> corners = cur != null ? new ArrayList<>(cur.corners) : new ArrayList<>();
        RegistryKey<World> key;
        try {
            key = player.getCommandSource().getWorld().getRegistryKey();
        } catch (Throwable ignored) {
            key = World.OVERWORLD;
        }

        // Enforce same world for all corners.
        if (cur != null && cur.worldKey != null && !cur.worldKey.equals(key)) {
            selections.remove(id);
            return -1;
        }

        int expectedY = cur != null ? cur.yLevel : pos.getY();

        // Check Y level matches
        if (!corners.isEmpty() && pos.getY() != expectedY) {
            debug.log("addCorner", "Y mismatch! New pos Y=" + pos.getY() + " expected Y=" + expectedY);
            // Reset on Y mismatch
            selections.remove(id);
            return -1;
        }

        // Prevent duplicates at same location
        if (corners.stream().anyMatch(c -> c.equals(pos))) {
            debug.log("addCorner", "Duplicate corner at " + pos);
            return corners.size(); // Return current count without adding
        }

        // Add corner
        corners.add(pos.toImmutable());
        int cornerNum = corners.size();

        debug.log("addCorner", "Added corner " + cornerNum + " at " + pos + " for player " + id);

        // Store updated selection
        selections.put(id, new MarkerSelection(corners, expectedY, false, key));

        // If all 4 corners placed, persist
        if (corners.size() == 4) {
            persistIfComplete(id);
        }

        return cornerNum;
    }

    /**
     * Clear markers for a player (left-click).
     */
    public void clearCorners(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean existed = selections.remove(id) != null;
        debug.log("clearCorners", "Cleared markers for player " + id + " (existed=" + existed + ")");
    }

    /**
     * Get current corners for a player.
     */
    public List<BlockPos> getCorners(ServerPlayerEntity player) {
        MarkerSelection sel = selections.get(player.getUuid());
        return sel != null ? new ArrayList<>(sel.corners) : new ArrayList<>();
    }

    /**
     * Check if player has all 4 corners set.
     */
    public boolean isComplete(ServerPlayerEntity player) {
        MarkerSelection sel = selections.get(player.getUuid());
        return sel != null && sel.corners.size() == 4;
    }

    /**
     * Check if player's frame is activated (wrench confirmed).
     */
    public boolean isActivated(UUID playerId) {
        MarkerSelection sel = selections.get(playerId);
        return sel != null && sel.activated;
    }

    /**
     * Activate the frame (wrench confirmation).
     */
    public void activateFrame(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MarkerSelection cur = selections.get(id);
        if (cur == null || cur.corners.size() < 4) return;

        selections.put(id, new MarkerSelection(cur.corners, cur.yLevel, true, cur.worldKey));
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
            selections.remove(playerId);
            return;
        }
        
        int yLevel = foundMarkers.get(0).getY();
        List<BlockPos> corners = new ArrayList<>();
        for (BlockPos pos : foundMarkers) {
            corners.add(pos.toImmutable());
        }

        RegistryKey<World> key = world != null ? world.getRegistryKey() : World.OVERWORLD;
        selections.put(playerId, new MarkerSelection(corners, yLevel, true, key));
        debug.log("setFrameFromBlocks", "Stored activated frame for player " + playerId + " from " + corners.size() + " markers");
        
        persistFrame(playerId);
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
        var snapshot = new java.util.ArrayList<>(selections.entrySet());

        for (var entry : snapshot) {
            UUID playerId = entry.getKey();
            MarkerSelection sel = entry.getValue();
            if (sel == null || !sel.activated || sel.corners == null || sel.corners.size() != 4) continue;

            ServerWorld world = server.getWorld(sel.worldKey != null ? sel.worldKey : World.OVERWORLD);
            if (world == null) continue;

            // If any marker is gone, deactivate the frame.
            boolean allPresent = true;
            for (BlockPos corner : sel.corners) {
                if (corner == null) continue;
                if (world.getBlockState(corner).getBlock() != dev.cloudframe.fabric.content.CloudFrameContent.MARKER_BLOCK) {
                    allPresent = false;
                    break;
                }
            }

            if (!allPresent) {
                selections.remove(playerId);
                debug.log("tick", "Deactivated frame for player " + playerId + " (marker missing)");
                continue;
            }

            spawnFrameLines(world, sel.corners);
        }
    }

    /**
     * Deactivate any active frame that contains the given marker position.
     */
    public void onMarkerBroken(ServerWorld world, BlockPos pos) {
        if (pos == null) return;
        RegistryKey<World> key = world != null ? world.getRegistryKey() : World.OVERWORLD;

        var snapshot = new java.util.ArrayList<>(selections.entrySet());
        for (var entry : snapshot) {
            UUID playerId = entry.getKey();
            MarkerSelection sel = entry.getValue();
            if (sel == null || !sel.activated || sel.corners == null) continue;
            if (sel.worldKey != null && sel.worldKey != key) continue;
            if (sel.corners.stream().anyMatch(pos::equals)) {
                selections.remove(playerId);
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
        MarkerSelection sel = selections.get(playerId);
        return sel != null ? new ArrayList<>(sel.corners) : new ArrayList<>();
    }

    /**
     * Clear markers for a player by UUID.
     */
    public void clearCorners(UUID playerId) {
        boolean existed = selections.remove(playerId) != null;
        debug.log("clearCorners", "Cleared markers for player " + playerId + " (existed=" + existed + ")");
    }

    public void loadAll() {
        selections.clear();
        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM markers");
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("player"));
                String worldName = null;
                try {
                    worldName = rs.getString("world");
                } catch (java.sql.SQLException ignored) {
                    worldName = null;
                }
                BlockPos a = new BlockPos(rs.getInt("ax"), rs.getInt("ay"), rs.getInt("az"));
                BlockPos b = new BlockPos(rs.getInt("bx"), rs.getInt("by"), rs.getInt("bz"));
                
                // Convert old 2-point format to 4-corner format (using corners as rectangle bounds)
                List<BlockPos> corners = new ArrayList<>();
                int minX = Math.min(a.getX(), b.getX());
                int maxX = Math.max(a.getX(), b.getX());
                int minZ = Math.min(a.getZ(), b.getZ());
                int maxZ = Math.max(a.getZ(), b.getZ());
                int y = a.getY();

                corners.add(new BlockPos(minX, y, minZ));
                corners.add(new BlockPos(maxX, y, minZ));
                corners.add(new BlockPos(maxX, y, maxZ));
                corners.add(new BlockPos(minX, y, maxZ));

                RegistryKey<World> key = World.OVERWORLD;
                try {
                    if (worldName != null && !worldName.isBlank()) {
                        key = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(worldName));
                    }
                } catch (Throwable ignored) {
                    key = World.OVERWORLD;
                }

                selections.put(id, new MarkerSelection(corners, y, false, key));
            }
        });
    }

    public void saveAll() {
        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM markers");
            var ps = conn.prepareStatement("INSERT INTO markers (player, world, ax, ay, az, bx, by, bz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            
            for (Map.Entry<UUID, MarkerSelection> e : selections.entrySet()) {
                UUID id = e.getKey();
                MarkerSelection sel = e.getValue();
                if (sel == null || sel.corners.size() < 2) continue;

                // Find bounds
                BlockPos first = sel.corners.get(0);
                int minX = first.getX();
                int maxX = first.getX();
                int minZ = first.getZ();
                int maxZ = first.getZ();

                for (BlockPos corner : sel.corners) {
                    minX = Math.min(minX, corner.getX());
                    maxX = Math.max(maxX, corner.getX());
                    minZ = Math.min(minZ, corner.getZ());
                    maxZ = Math.max(maxZ, corner.getZ());
                }

                ps.setString(1, id.toString());
                String worldName = "minecraft:overworld";
                try {
                    if (sel.worldKey != null) {
                        worldName = sel.worldKey.getValue().toString();
                    }
                } catch (Throwable ignored) {
                    worldName = "minecraft:overworld";
                }
                ps.setString(2, worldName);
                ps.setInt(3, minX);
                ps.setInt(4, first.getY());
                ps.setInt(5, minZ);
                ps.setInt(6, maxX);
                ps.setInt(7, first.getY());
                ps.setInt(8, maxZ);
                ps.addBatch();
            }
            ps.executeBatch();
        });
    }

    private void persistIfComplete(UUID id) {
        MarkerSelection sel = selections.get(id);
        if (sel == null || sel.corners.size() < 4) return;

        debug.log("persistIfComplete", "All 4 corners set for player " + id + ", persisting...");

        Database.run(conn -> {
            var del = conn.prepareStatement("DELETE FROM markers WHERE player = ?");
            del.setString(1, id.toString());
            del.executeUpdate();

            // Find bounds
            BlockPos first = sel.corners.get(0);
            int minX = first.getX();
            int maxX = first.getX();
            int minZ = first.getZ();
            int maxZ = first.getZ();

            for (BlockPos corner : sel.corners) {
                minX = Math.min(minX, corner.getX());
                maxX = Math.max(maxX, corner.getX());
                minZ = Math.min(minZ, corner.getZ());
                maxZ = Math.max(maxZ, corner.getZ());
            }

            var ps = conn.prepareStatement("INSERT INTO markers (player, world, ax, ay, az, bx, by, bz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, id.toString());
            String worldName = "minecraft:overworld";
            try {
                if (sel.worldKey != null) {
                    worldName = sel.worldKey.getValue().toString();
                }
            } catch (Throwable ignored) {
                worldName = "minecraft:overworld";
            }
            ps.setString(2, worldName);
            ps.setInt(3, minX);
            ps.setInt(4, first.getY());
            ps.setInt(5, minZ);
            ps.setInt(6, maxX);
            ps.setInt(7, first.getY());
            ps.setInt(8, maxZ);
            ps.executeUpdate();
        });

        debug.log("persistIfComplete", "Markers persisted. Awaiting wrench confirmation.");
    }

    private void persistFrame(UUID id) {
        MarkerSelection sel = selections.get(id);
        if (sel == null || sel.corners.size() < 4) return;

        debug.log("persistFrame", "Persisting activated frame for player " + id);

        Database.run(conn -> {
            var del = conn.prepareStatement("DELETE FROM markers WHERE player = ?");
            del.setString(1, id.toString());
            del.executeUpdate();

            // Find bounds
            BlockPos first = sel.corners.get(0);
            int minX = first.getX();
            int maxX = first.getX();
            int minZ = first.getZ();
            int maxZ = first.getZ();

            for (BlockPos corner : sel.corners) {
                minX = Math.min(minX, corner.getX());
                maxX = Math.max(maxX, corner.getX());
                minZ = Math.min(minZ, corner.getZ());
                maxZ = Math.max(maxZ, corner.getZ());
            }

            var ps = conn.prepareStatement("INSERT INTO markers (player, world, ax, ay, az, bx, by, bz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, id.toString());
            String worldName = "minecraft:overworld";
            try {
                if (sel.worldKey != null) {
                    worldName = sel.worldKey.getValue().toString();
                }
            } catch (Throwable ignored) {
                worldName = "minecraft:overworld";
            }
            ps.setString(2, worldName);
            ps.setInt(3, minX);
            ps.setInt(4, first.getY());
            ps.setInt(5, minZ);
            ps.setInt(6, maxX);
            ps.setInt(7, first.getY());
            ps.setInt(8, maxZ);
            ps.executeUpdate();
        });

        debug.log("persistFrame", "Frame persisted to database.");
    }
}
