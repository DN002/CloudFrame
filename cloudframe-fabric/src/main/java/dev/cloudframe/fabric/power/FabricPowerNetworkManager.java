package dev.cloudframe.fabric.power;

import java.util.List;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.content.CloudCellBlockEntity;
import dev.cloudframe.fabric.power.EnergyInterop;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.common.power.PowerNetworkManager;
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

    private static final PowerNetworkManager MANAGER = new PowerNetworkManager(
        new PowerNetworkManager.LocationAdapter() {
            @Override
            public Object normalize(Object loc) {
                if (!(loc instanceof GlobalPos gp)) return null;
                return GlobalPos.create(gp.dimension(), gp.pos().toImmutable());
            }

            @Override
            public Object offset(Object loc, int dx, int dy, int dz) {
                if (!(loc instanceof GlobalPos gp)) return null;
                BlockPos p = gp.pos();
                return GlobalPos.create(gp.dimension(), new BlockPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
            }

            @Override
            public String worldId(Object loc) {
                if (!(loc instanceof GlobalPos gp)) return null;
                return gp.dimension().getValue().toString();
            }

            @Override
            public int blockX(Object loc) {
                if (!(loc instanceof GlobalPos gp)) return 0;
                return gp.pos().getX();
            }

            @Override
            public int blockY(Object loc) {
                if (!(loc instanceof GlobalPos gp)) return 0;
                return gp.pos().getY();
            }

            @Override
            public int blockZ(Object loc) {
                if (!(loc instanceof GlobalPos gp)) return 0;
                return gp.pos().getZ();
            }
        },
        new PowerNetworkManager.Access() {
            @Override
            public boolean isCable(Object ctx, Object loc) {
                if (!(ctx instanceof MinecraftServer server)) return false;
                if (!(loc instanceof GlobalPos gp)) return false;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return false;
                return CloudFrameContent.getCloudCableBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getCloudCableBlock());
            }

            @Override
            public boolean isCableSideDisabled(Object ctx, Object cableLoc, int dirIndex) {
                if (!(cableLoc instanceof GlobalPos gp)) return false;
                CloudFrameFabric instance = CloudFrameFabric.instance();
                if (instance == null || instance.getCableConnectionManager() == null) return false;
                return instance.getCableConnectionManager().isSideDisabled(gp, dirIndex);
            }

            @Override
            public boolean isProducer(Object ctx, Object loc) {
                if (!(ctx instanceof MinecraftServer server)) return false;
                if (!(loc instanceof GlobalPos gp)) return false;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return false;

                return (CloudFrameContent.getStratusPanelBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getStratusPanelBlock()))
                    || (CloudFrameContent.getCloudTurbineBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getCloudTurbineBlock()));
            }

            @Override
            public long producerCfePerTick(Object ctx, Object producerLoc) {
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(producerLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;

                if (CloudFrameContent.getStratusPanelBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getStratusPanelBlock())) {
                    return measureStratusPanelCfePerTick(w, gp.pos());
                }

                if (CloudFrameContent.getCloudTurbineBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getCloudTurbineBlock())) {
                    return measureCloudTurbineCfePerTick();
                }

                return 0L;
            }

            @Override
            public boolean isCell(Object ctx, Object loc) {
                if (!(ctx instanceof MinecraftServer server)) return false;
                if (!(loc instanceof GlobalPos gp)) return false;
                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return false;

                return CloudFrameContent.getCloudCellBlock() != null && w.getBlockState(gp.pos()).isOf(CloudFrameContent.getCloudCellBlock());
            }

            @Override
            public long cellInsertCfe(Object ctx, Object cellLoc, long amount) {
                if (amount <= 0L) return 0L;
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(cellLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;
                if (!(w.getBlockEntity(gp.pos()) instanceof CloudCellBlockEntity be)) return 0L;
                return be.insertCfe(amount);
            }

            @Override
            public long cellExtractCfe(Object ctx, Object cellLoc, long amount) {
                if (amount <= 0L) return 0L;
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(cellLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;
                if (!(w.getBlockEntity(gp.pos()) instanceof CloudCellBlockEntity be)) return 0L;
                return be.extractCfe(amount);
            }

            @Override
            public long cellStoredCfe(Object ctx, Object cellLoc) {
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(cellLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;
                if (!(w.getBlockEntity(gp.pos()) instanceof CloudCellBlockEntity be)) return 0L;
                return Math.max(0L, be.getStoredCfe());
            }

            @Override
            public boolean externalApiPresent(Object ctx) {
                return EnergyInterop.isAvailable();
            }

            @Override
            public boolean isExternalStorage(Object ctx, Object externalLoc, int sideDirIndex) {
                if (!(ctx instanceof MinecraftServer server)) return false;
                if (!(externalLoc instanceof GlobalPos gp)) return false;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return false;

                Direction side = toDirection(sideDirIndex);
                if (side == null) return false;

                // Best-effort: if measure returns non-null, treat as a storage.
                return EnergyInterop.tryMeasureExternalCfe(w, gp.pos(), side) != null;
            }

            @Override
            public long externalExtractCfe(Object ctx, Object externalLoc, int sideDirIndex, long amount) {
                if (amount <= 0L) return 0L;
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(externalLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;

                Direction side = toDirection(sideDirIndex);
                if (side == null) return 0L;

                return EnergyInterop.tryExtractExternalCfe(w, gp.pos(), side, amount);
            }

            @Override
            public long externalStoredCfe(Object ctx, Object externalLoc, int sideDirIndex) {
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(externalLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;

                Direction side = toDirection(sideDirIndex);
                if (side == null) return 0L;

                var info = EnergyInterop.tryMeasureExternalCfe(w, gp.pos(), side);
                return info == null ? 0L : Math.max(0L, info.storedCfe());
            }

            @Override
            public long externalCapacityCfe(Object ctx, Object externalLoc, int sideDirIndex) {
                if (!(ctx instanceof MinecraftServer server)) return 0L;
                if (!(externalLoc instanceof GlobalPos gp)) return 0L;

                ServerWorld w = server.getWorld(gp.dimension());
                if (w == null) return 0L;

                Direction side = toDirection(sideDirIndex);
                if (side == null) return 0L;

                var info = EnergyInterop.tryMeasureExternalCfe(w, gp.pos(), side);
                return info == null ? 0L : Math.max(0L, info.capacityCfe());
            }
        }
    );

    public static void beginTick(MinecraftServer server, long tick) {
        MANAGER.beginTick(server, tick);
    }

    /**
     * Called after consumers have run for the tick.
     * Best-effort: store any unused per-tick generation into available Cloud Cells.
     */
    public static void endTick(MinecraftServer server) {
        MANAGER.endTick(server);
    }

    public static long extractPowerCfe(MinecraftServer server, Object controllerLoc, long amount) {
        return MANAGER.extractPowerCfe(server, controllerLoc, amount);
    }

    /**
     * Extract ONLY from the network's per-tick generation budget (no cells, no external storages).
     *
     * This is used for features like the quarry controller buffer: we only want to store "surplus"
     * generation and avoid pulling from batteries/cells.
     */
    public static long extractGenerationOnlyCfe(MinecraftServer server, Object controllerLoc, long amount) {
        return MANAGER.extractGenerationOnlyCfe(server, controllerLoc, amount);
    }

    /**
     * Read-only view of a cable network's current potential generation and stored energy.
     * This does NOT consume power. Intended for UI/status display.
     */
    public record NetworkInfo(long producedCfePerTick, long storedCfe) {
    }

    public static long measureStratusPanelCfePerTick(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return 0L;
        if (CloudFrameContent.getStratusPanelBlock() == null) return 0L;
        if (!world.getBlockState(pos).isOf(CloudFrameContent.getStratusPanelBlock())) return 0L;

        int sky = world.getLightLevel(LightType.SKY, pos.up());
        if (sky < 0) sky = 0;
        if (sky > 15) sky = 15;

        int gen = (8 * sky) / 15;
        if (gen <= 0) return 0L;

        if (world.isThundering()) {
            gen = gen / 2;
        } else if (world.isRaining()) {
            gen = (gen * 3) / 4;
        }

        return Math.max(0L, (long) gen);
    }

    public static long measureCloudTurbineCfePerTick() {
        return 32L;
    }

    public static NetworkInfo measureCableNetwork(MinecraftServer server, GlobalPos cablePos) {
        PowerNetworkManager.NetworkInfo info = MANAGER.measureCableNetwork(server, cablePos);
        return new NetworkInfo(info.producedCfePerTick(), info.storedCfe());
    }

    public record CableProbeInfo(
        long producedCfePerTick,
        long storedCfe,
        boolean externalApiPresent,
        int externalEndpointCount,
        long externalStoredCfe,
        long externalCapacityCfe
    ) {
    }

    public static CableProbeInfo measureCableNetworkForProbe(MinecraftServer server, GlobalPos cablePos) {
        PowerNetworkManager.CableProbeInfo info = MANAGER.measureCableNetworkForProbe(server, cablePos);
        return new CableProbeInfo(
            info.producedCfePerTick(),
            info.storedCfe(),
            info.externalApiPresent(),
            info.externalEndpointCount(),
            info.externalStoredCfe(),
            info.externalCapacityCfe()
        );
    }

    /**
     * Read-only probe info for the cable network adjacent to a controller block.
     * This does NOT consume power.
     */
    public static CableProbeInfo measureControllerNetworkForProbe(MinecraftServer server, GlobalPos controllerPos) {
        PowerNetworkManager.CableProbeInfo info = MANAGER.measureNetworkForProbe(server, controllerPos);
        return new CableProbeInfo(
            info.producedCfePerTick(),
            info.storedCfe(),
            info.externalApiPresent(),
            info.externalEndpointCount(),
            info.externalStoredCfe(),
            info.externalCapacityCfe()
        );
    }

    public static NetworkInfo measureNetwork(MinecraftServer server, Object controllerLoc) {
        PowerNetworkManager.NetworkInfo info = MANAGER.measureNetwork(server, controllerLoc);
        return new NetworkInfo(info.producedCfePerTick(), info.storedCfe());
    }

    private static Direction toDirection(int dirIndex) {
        return switch (dirIndex) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.WEST;
            case 2 -> Direction.UP;
            case 3 -> Direction.DOWN;
            case 4 -> Direction.SOUTH;
            case 5 -> Direction.NORTH;
            default -> null;
        };
    }
}
