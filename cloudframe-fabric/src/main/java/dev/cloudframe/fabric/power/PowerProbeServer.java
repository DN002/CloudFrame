package dev.cloudframe.fabric.power;

import dev.cloudframe.fabric.content.CloudFrameContent;
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
