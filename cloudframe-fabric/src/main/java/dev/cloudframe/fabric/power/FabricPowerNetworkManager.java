package dev.cloudframe.fabric.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.content.CloudCellBlockEntity;
import dev.cloudframe.fabric.power.EnergyInterop;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * Minimal power-network manager for Cloud Cables.
 *
 * - Networks are connected components of Cloud Cable blocks.
 * - Producers: Stratus Panel (8 CFE/t max, skylight-scaled, weather-reduced), Cloud Turbine (32 CFE/t flat).
 * - Storage: Cloud Cell block entity (1,000,000 CFE cap).
 *
 * This is intentionally simple: it builds a network snapshot on demand per tick and shares
 * per-tick generation via a remainingGeneration budget.
 */
public final class FabricPowerNetworkManager {

    private FabricPowerNetworkManager() {
    }

    private static final int BFS_NODE_LIMIT = 8192;

    private static long currentTick = -1L;
    private static final Map<NetworkKey, NetworkSnapshot> snapshots = new HashMap<>();

    private record NetworkKey(net.minecraft.registry.RegistryKey<World> dimension, long rootCablePosLong) {
    }

    private static final class NetworkSnapshot {
        final long tick;
        long remainingGenerationCfe;
        final List<GlobalPos> cells;
        final List<ExternalEndpoint> external;

        NetworkSnapshot(long tick, long producedCfe, List<GlobalPos> cells, List<ExternalEndpoint> external) {
            this.tick = tick;
            this.remainingGenerationCfe = Math.max(0L, producedCfe);
            this.cells = cells;
            this.external = external;
        }
    }

    private record ExternalEndpoint(GlobalPos pos, Direction side) {
    }

    public static void beginTick(MinecraftServer server, long tick) {
        if (tick == currentTick) return;
        currentTick = tick;
        snapshots.clear();
    }

    /**
     * Called after consumers have run for the tick.
     * Best-effort: store any unused per-tick generation into available Cloud Cells.
     */
    public static void endTick(MinecraftServer server) {
        if (snapshots.isEmpty()) return;

        for (NetworkSnapshot snap : snapshots.values()) {
            long remaining = snap.remainingGenerationCfe;
            if (remaining <= 0L) continue;

            for (GlobalPos cellPos : snap.cells) {
                if (remaining <= 0L) break;
                ServerWorld w = server.getWorld(cellPos.dimension());
                if (w == null) continue;
                if (!(w.getBlockEntity(cellPos.pos()) instanceof CloudCellBlockEntity be)) continue;
                remaining = remaining - be.insertCfe(remaining);
            }

            snap.remainingGenerationCfe = 0L;
        }
    }

    public static long extractPowerCfe(MinecraftServer server, Object controllerLoc, long amount) {
        if (amount <= 0L) return 0L;
        if (!(controllerLoc instanceof GlobalPos controller)) return 0L;

        ServerWorld world = server.getWorld(controller.dimension());
        if (world == null) return 0L;

        NetworkDiscovery discovery = discoverNetwork(world, controller.pos());
        if (discovery == null) return 0L;

        NetworkKey key = new NetworkKey(world.getRegistryKey(), discovery.rootCablePosLong);
        NetworkSnapshot snap = snapshots.get(key);
        if (snap == null || snap.tick != currentTick) {
            snap = new NetworkSnapshot(currentTick, discovery.producedCfePerTick, discovery.cells, discovery.external);
            snapshots.put(key, snap);
        }

        long extracted = 0L;

        // Consume per-tick generation first.
        long takeGen = Math.min(amount, snap.remainingGenerationCfe);
        if (takeGen > 0L) {
            snap.remainingGenerationCfe -= takeGen;
            extracted += takeGen;
            amount -= takeGen;
        }

        // Then pull from storage.
        if (amount > 0L && !snap.cells.isEmpty()) {
            for (GlobalPos cellPos : snap.cells) {
                if (amount <= 0L) break;
                ServerWorld w = server.getWorld(cellPos.dimension());
                if (w == null) continue;
                if (!(w.getBlockEntity(cellPos.pos()) instanceof CloudCellBlockEntity be)) continue;

                long got = be.extractCfe(amount);
                if (got > 0L) {
                    extracted += got;
                    amount -= got;
                }
            }
        }

        // Finally, pull from any external energy storages adjacent to the cable network.
        // Soft dependency: only active if the external energy API mod is present.
        if (amount > 0L && EnergyInterop.isAvailable() && snap.external != null && !snap.external.isEmpty()) {
            for (ExternalEndpoint ep : snap.external) {
                if (amount <= 0L) break;
                ServerWorld w = server.getWorld(ep.pos().dimension());
                if (w == null) continue;
                long got = EnergyInterop.tryExtractExternalCfe(w, ep.pos().pos(), ep.side(), amount);
                if (got > 0L) {
                    extracted += got;
                    amount -= got;
                }
            }
        }

        return extracted;
    }

    private static final class NetworkDiscovery {
        final long rootCablePosLong;
        final long producedCfePerTick;
        final List<GlobalPos> cells;
        final List<ExternalEndpoint> external;

        NetworkDiscovery(long rootCablePosLong, long producedCfePerTick, List<GlobalPos> cells, List<ExternalEndpoint> external) {
            this.rootCablePosLong = rootCablePosLong;
            this.producedCfePerTick = producedCfePerTick;
            this.cells = cells;
            this.external = external;
        }
    }

    private static NetworkDiscovery discoverNetwork(ServerWorld world, BlockPos controllerPos) {
        // Start from adjacent cables (controller does not count as a cable node).
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();

        for (Direction d : Direction.values()) {
            BlockPos adj = controllerPos.offset(d);
            if (isCable(world, adj)) {
                long k = adj.asLong();
                if (visited.add(k)) {
                    queue.add(adj);
                }
            }
        }

        if (queue.isEmpty()) return null;

        long root = Long.MAX_VALUE;
        int nodes = 0;

        // Keep unique producer positions to avoid double counting.
        HashSet<Long> stratusPanels = new HashSet<>();
        HashSet<Long> turbines = new HashSet<>();
        HashSet<Long> cells = new HashSet<>();
        HashSet<Long> externalKeys = new HashSet<>();
        List<ExternalEndpoint> externalEndpoints = new ArrayList<>();

        while (!queue.isEmpty() && nodes < BFS_NODE_LIMIT) {
            BlockPos pos = queue.poll();
            if (pos == null) continue;
            long key = pos.asLong();
            nodes++;

            if (key < root) root = key;

            // Discover adjacent producers/storage.
            for (Direction d : Direction.values()) {
                BlockPos n = pos.offset(d);
                if (isStratusPanel(world, n)) {
                    stratusPanels.add(n.asLong());
                } else if (isCloudTurbine(world, n)) {
                    turbines.add(n.asLong());
                } else if (isCloudCell(world, n)) {
                    cells.add(n.asLong());
                } else if (EnergyInterop.isAvailable() && !isCable(world, n)) {
                    // Candidate external energy storage adjacent to the cable graph.
                    // We store the neighbor position and the side on that neighbor that faces the cable.
                    long k2 = (n.asLong() ^ ((long) d.getOpposite().ordinal() << 56));
                    if (externalKeys.add(k2)) {
                        externalEndpoints.add(new ExternalEndpoint(
                            GlobalPos.create(world.getRegistryKey(), n),
                            d.getOpposite()
                        ));
                    }
                }
            }

            // Traverse to adjacent cables.
            for (Direction d : Direction.values()) {
                BlockPos n = pos.offset(d);
                if (!isCable(world, n)) continue;
                long nk = n.asLong();
                if (visited.add(nk)) {
                    queue.add(n);
                }
            }
        }

        if (root == Long.MAX_VALUE) return null;

        long produced = 0L;

        // Stratus panels: skylight scaled (0..15) -> 0..8 CFE/t.
        boolean thunder = world.isThundering();
        boolean rain = world.isRaining();

        for (long p : stratusPanels) {
            BlockPos pos = BlockPos.fromLong(p);
            int sky = world.getLightLevel(LightType.SKY, pos.up());
            if (sky < 0) sky = 0;
            if (sky > 15) sky = 15;

            int gen = (8 * sky) / 15;
            if (gen <= 0) continue;

            if (thunder) {
                gen = gen / 2;
            } else if (rain) {
                gen = (gen * 3) / 4;
            }

            produced += Math.max(0, gen);
        }

        // Turbines: flat output.
        produced += 32L * (long) turbines.size();

        List<GlobalPos> cellPositions = new ArrayList<>();
        if (!cells.isEmpty()) {
            for (long c : cells) {
                cellPositions.add(GlobalPos.create(world.getRegistryKey(), BlockPos.fromLong(c)));
            }
        }

        return new NetworkDiscovery(root, produced, cellPositions, externalEndpoints);
    }

    private static boolean isCable(ServerWorld world, BlockPos pos) {
        return CloudFrameContent.getCloudCableBlock() != null && world.getBlockState(pos).isOf(CloudFrameContent.getCloudCableBlock());
    }

    private static boolean isStratusPanel(ServerWorld world, BlockPos pos) {
        return CloudFrameContent.getStratusPanelBlock() != null && world.getBlockState(pos).isOf(CloudFrameContent.getStratusPanelBlock());
    }

    private static boolean isCloudTurbine(ServerWorld world, BlockPos pos) {
        return CloudFrameContent.getCloudTurbineBlock() != null && world.getBlockState(pos).isOf(CloudFrameContent.getCloudTurbineBlock());
    }

    private static boolean isCloudCell(ServerWorld world, BlockPos pos) {
        return CloudFrameContent.getCloudCellBlock() != null && world.getBlockState(pos).isOf(CloudFrameContent.getCloudCellBlock());
    }
}
