package dev.cloudframe.fabric.power;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.common.quarry.Quarry;
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

        int type = PowerProbePackets.TYPE_NONE;
        long produced = 0L;
        long stored = 0L;
        boolean externalApiPresent = EnergyInterop.isAvailable();
        int externalEndpointCount = 0;
        long externalStored = 0L;
        long externalCapacity = 0L;

        if (CloudFrameContent.getCloudCableBlock() != null && state.isOf(CloudFrameContent.getCloudCableBlock())) {
            type = PowerProbePackets.TYPE_CABLE;
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
            type = PowerProbePackets.TYPE_QUARRY_CONTROLLER;
            CloudFrameFabric inst = CloudFrameFabric.instance();
            Quarry q = (inst != null && inst.getQuarryManager() != null)
                ? inst.getQuarryManager().getByController(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()))
                : null;

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

            // For controllers, the probe is meant to be a quick read of the quarry's own power draw.
            // We encode: produced = receiving (actual), stored = using (required).
            if (q != null && q.isActive()) {
                produced = Math.max(0L, q.getPowerReceivedCfePerTick());
                stored = Math.max(0L, q.getPowerRequiredCfePerTick());
            } else {
                produced = 0L;
                stored = 0L;
            }

            // No external segment for controller probe.
            externalApiPresent = false;
            // Reuse this int field for quarry/controller state (0..4) to avoid expanding the payload.
            externalEndpointCount = controllerState;
            externalStored = 0L;
            externalCapacity = 0L;
        } else if (CloudFrameContent.getStratusPanelBlock() != null && state.isOf(CloudFrameContent.getStratusPanelBlock())) {
            type = PowerProbePackets.TYPE_STRATUS_PANEL;
            produced = FabricPowerNetworkManager.measureStratusPanelCfePerTick(world, pos);
        } else if (CloudFrameContent.getCloudTurbineBlock() != null && state.isOf(CloudFrameContent.getCloudTurbineBlock())) {
            type = PowerProbePackets.TYPE_CLOUD_TURBINE;
            produced = FabricPowerNetworkManager.measureCloudTurbineCfePerTick();
        }

        ServerPlayNetworking.send(player, new PowerProbeResponsePayload(
            pos.asLong(),
            type,
            produced,
            stored,
            externalApiPresent ? 1 : 0,
            externalEndpointCount,
            externalStored,
            externalCapacity
        ));
    }
}
