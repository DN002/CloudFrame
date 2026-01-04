package dev.cloudframe.fabric.quarry.controller;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import dev.cloudframe.common.quarry.Quarry;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

/**
 * Per-player toggle to continuously outline the chunkloading chunks with particles.
 * Off by default; not persisted.
 */
public final class ChunkPreviewService {

    private ChunkPreviewService() {}

    private static final class Session {
        final GlobalPos controllerLoc;
        long nextTick;

        Session(GlobalPos controllerLoc, long nextTick) {
            this.controllerLoc = controllerLoc;
            this.nextTick = nextTick;
        }
    }

    // playerUuid -> session
    private static final Map<java.util.UUID, Session> sessions = new HashMap<>();

    // How often to refresh the outline (ticks)
    private static final long PERIOD_TICKS = 10L;

    public static boolean isEnabled(ServerPlayerEntity player) {
        if (player == null) return false;
        return sessions.containsKey(player.getUuid());
    }

    /**
     * Toggle the preview for a player. Returns the new enabled state.
     */
    public static boolean toggle(ServerPlayerEntity player, Quarry quarry, GlobalPos controllerLoc) {
        if (player == null || quarry == null || controllerLoc == null) return false;

        java.util.UUID id = player.getUuid();
        if (sessions.containsKey(id)) {
            sessions.remove(id);
            return false;
        }

        // nextTick=0 so it draws immediately on next service tick.
        sessions.put(id, new Session(controllerLoc, 0L));
        return true;
    }

    public static void tick(MinecraftServer server, long tickCounter) {
        if (server == null) return;
        if (sessions.isEmpty()) return;

        Iterator<Map.Entry<java.util.UUID, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<java.util.UUID, Session> entry = it.next();
            java.util.UUID playerId = entry.getKey();
            Session session = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null || player.isDisconnected()) {
                it.remove();
                continue;
            }

            if (tickCounter < session.nextTick) {
                continue;
            }
            session.nextTick = tickCounter + PERIOD_TICKS;

            // Quarry lookup: only show for the controller this session was created for.
            dev.cloudframe.fabric.CloudFrameFabric inst = dev.cloudframe.fabric.CloudFrameFabric.instance();
            if (inst == null || inst.getQuarryManager() == null) continue;
            Quarry q = inst.getQuarryManager().getByController(session.controllerLoc);
            if (q == null) {
                // Controller no longer registered; stop preview.
                it.remove();
                continue;
            }

            // Only render in the world the session was created for, and only if the player
            // is currently in that same world. This prevents particles from ever appearing
            // in other dimensions.
            RegistryKey<World> key = session.controllerLoc.dimension();
            RegistryKey<World> playerKey;
            try {
                playerKey = player.getCommandSource().getWorld().getRegistryKey();
            } catch (Throwable ignored) {
                playerKey = World.OVERWORLD;
            }
            if (!playerKey.equals(key)) {
                continue;
            }

            ServerWorld sw = server.getWorld(key);
            if (sw == null) continue;

            var region = q.getRegion();
            if (region == null) continue;

            // Extra safety: ensure the quarry region world matches the preview world.
            if (region.getWorld() instanceof ServerWorld qWorld) {
                if (!qWorld.getRegistryKey().equals(key)) {
                    continue;
                }
            }

            Set<Long> chunks = new HashSet<>();

            int minCx = region.minX() >> 4;
            int maxCx = region.maxX() >> 4;
            int minCz = region.minZ() >> 4;
            int maxCz = region.maxZ() >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    chunks.add((((long) cx) << 32) | (cz & 0xffffffffL));
                }
            }

            BlockPos ctrlPos = session.controllerLoc.pos();
            int ccx = ctrlPos.getX() >> 4;
            int ccz = ctrlPos.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = ccx + dx;
                    int cz = ccz + dz;
                    chunks.add((((long) cx) << 32) | (cz & 0xffffffffL));
                }
            }

            int y = ctrlPos.getY() + 1;
            int step = 4;

            for (long chunkKey : chunks) {
                int cx = (int) (chunkKey >> 32);
                int cz = (int) chunkKey;
                int x0 = cx << 4;
                int z0 = cz << 4;
                int x1 = x0 + 16;
                int z1 = z0 + 16;

                for (int x = x0; x <= x1; x += step) {
                    sw.spawnParticles(player, ParticleTypes.END_ROD, true, false,
                        x + 0.5, y + 0.1, z0 + 0.5, 1, 0, 0, 0, 0);
                    sw.spawnParticles(player, ParticleTypes.END_ROD, true, false,
                        x + 0.5, y + 0.1, z1 + 0.5, 1, 0, 0, 0, 0);
                }
                for (int z = z0; z <= z1; z += step) {
                    sw.spawnParticles(player, ParticleTypes.END_ROD, true, false,
                        x0 + 0.5, y + 0.1, z + 0.5, 1, 0, 0, 0, 0);
                    sw.spawnParticles(player, ParticleTypes.END_ROD, true, false,
                        x1 + 0.5, y + 0.1, z + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
