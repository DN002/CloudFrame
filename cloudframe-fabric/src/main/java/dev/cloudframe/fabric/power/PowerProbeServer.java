package dev.cloudframe.fabric.power;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.common.power.probe.PowerProbeSnapshot;
import dev.cloudframe.common.power.probe.PowerProbeType;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

public final class PowerProbeServer {

    private PowerProbeServer() {
    }

    public static void register() {
        PowerProbePayloads.register();

        ServerPlayNetworking.registerGlobalReceiver(PowerProbeRequestPayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            ServerPlayerEntity player = context.player();
            server.execute(() -> handle(server, player, BlockPos.fromLong(payload.posLong())));
        });
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, BlockPos pos) {
        if (server == null || player == null || pos == null) return;

        ServerWorld world = player.getEntityWorld();
        if (world == null) return;

        BlockState state = world.getBlockState(pos);

        PowerProbeType type = PowerProbeType.NONE;
        long produced = 0L;
        long stored = 0L;
        boolean externalApiPresent = EnergyInterop.isAvailable();
        int externalEndpointCount = 0;
        long externalStored = 0L;
        long externalCapacity = 0L;

        if (CloudFrameContent.getCloudCableBlock() != null && state.isOf(CloudFrameContent.getCloudCableBlock())) {
            type = PowerProbeType.CABLE;
            FabricPowerNetworkManager.CableProbeInfo info = FabricPowerNetworkManager.measureCableNetworkForProbe(
                server,
                GlobalPos.create(world.getRegistryKey(), pos)
            );
            produced = info.producedCfePerTick();
            stored = info.storedCfe();
            externalApiPresent = info.externalApiPresent();
            externalEndpointCount = info.externalEndpointCount();
            externalStored = info.externalStoredCfe();
            externalCapacity = info.externalCapacityCfe();
        } else if (CloudFrameContent.getQuarryControllerBlock() != null && state.isOf(CloudFrameContent.getQuarryControllerBlock())) {
            type = PowerProbeType.QUARRY_CONTROLLER;
            CloudFrameFabric inst = CloudFrameFabric.instance();
            GlobalPos controllerLoc = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
            Quarry q = (inst != null && inst.getQuarryManager() != null)
                ? inst.getQuarryManager().getByController(controllerLoc)
                : null;

            QuarryControllerBlockEntity be = null;
            if (world.getBlockEntity(pos) instanceof QuarryControllerBlockEntity qbe) {
                be = qbe;
            }

            int controllerState;
            if (q == null) {
                controllerState = 0;
            } else if (q.isScanningMetadata()) {
                controllerState = 4;
            } else if (q.isScanning()) {
                controllerState = 3;
            } else if (q.isActive()) {
                controllerState = 2;
            } else {
                controllerState = 1;
            }

            // Controller probe semantics:
            // - produced: "Receiving" = available generation on the connected cable network (matches cable probe expectation)
            // - stored: "Using" = quarry's required draw when active, otherwise 0
            // - externalStored: controller buffer stored CFE
            // (externalEndpointCount is reused for controller state)
            FabricPowerNetworkManager.NetworkInfo info = FabricPowerNetworkManager.measureNetwork(server, controllerLoc);
            produced = info != null ? Math.max(0L, info.producedCfePerTick()) : 0L;

            stored = (q != null && q.isActive())
                ? Math.max(0L, q.getPowerRequiredCfePerTick())
                : 0L;

            externalStored = (be != null) ? Math.max(0L, be.getPowerBufferStoredCfe()) : 0L;

            // No external segment for controller probe.
            externalApiPresent = false;
            // Reuse this int field for quarry/controller state (0..4) to avoid expanding the payload.
            externalEndpointCount = controllerState;
            externalCapacity = 0L;
        } else if (CloudFrameContent.getStratusPanelBlock() != null && state.isOf(CloudFrameContent.getStratusPanelBlock())) {
            type = PowerProbeType.STRATUS_PANEL;
            produced = FabricPowerNetworkManager.measureStratusPanelCfePerTick(world, pos);
        } else if (CloudFrameContent.getCloudTurbineBlock() != null && state.isOf(CloudFrameContent.getCloudTurbineBlock())) {
            type = PowerProbeType.CLOUD_TURBINE;
            produced = FabricPowerNetworkManager.measureCloudTurbineCfePerTick();
        }

        PowerProbeSnapshot snap = new PowerProbeSnapshot(
            pos.asLong(),
            type,
            produced,
            stored,
            externalApiPresent,
            externalEndpointCount,
            externalStored,
            externalCapacity
        );

        ServerPlayNetworking.send(player, new PowerProbeResponsePayload(
            snap.posLong(),
            snap.type().legacyId(),
            snap.producedCfePerTick(),
            snap.storedCfe(),
            snap.externalApiPresent() ? 1 : 0,
            snap.externalEndpointCount(),
            snap.externalStoredCfe(),
            snap.externalCapacityCfe()
        ));
    }
}
