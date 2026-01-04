package dev.cloudframe.fabric.client;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.power.PowerProbePackets;
import dev.cloudframe.fabric.power.PowerProbePayloads;
import dev.cloudframe.fabric.power.PowerProbeRequestPayload;
import dev.cloudframe.fabric.power.PowerProbeResponsePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class PowerProbeClient {

    private PowerProbeClient() {
    }

    private static long lastTargetPosLong = Long.MIN_VALUE;
    private static int requestCooldownTicks = 0;

    private static long lastResponsePosLong = Long.MIN_VALUE;
    private static Text lastResponseText = null;

    private static boolean wasShowing = false;

    public static void register() {
        PowerProbePayloads.register();

        ClientPlayNetworking.registerGlobalReceiver(PowerProbeResponsePayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                lastResponsePosLong = payload.posLong();
                lastResponseText = format(
                    payload.type(),
                    payload.produced(),
                    payload.stored(),
                    payload.externalApiPresentFlag() != 0,
                    payload.externalEndpointCount(),
                    payload.externalStored(),
                    payload.externalCapacity()
                );
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(PowerProbeClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return;

        if (!isHoldingWrench(client.player.getMainHandStack()) && !isHoldingWrench(client.player.getOffHandStack())) {
            clearActionbarIfNeeded(client);
            resetState();
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            clearActionbarIfNeeded(client);
            resetState();
            return;
        }

        BlockPos pos = bhr.getBlockPos();
        BlockState state = client.world.getBlockState(pos);

        if (!isProbeTarget(state)) {
            clearActionbarIfNeeded(client);
            resetState();
            return;
        }

        long posLong = pos.asLong();
        if (posLong != lastTargetPosLong) {
            lastTargetPosLong = posLong;
            requestCooldownTicks = 0;
        }

        if (requestCooldownTicks <= 0) {
            ClientPlayNetworking.send(new PowerProbeRequestPayload(posLong));
            requestCooldownTicks = 5;
        } else {
            requestCooldownTicks--;
        }

        if (lastResponseText != null && lastResponsePosLong == posLong) {
            client.player.sendMessage(lastResponseText, true);
            wasShowing = true;
        } else {
            clearActionbarIfNeeded(client);
        }
    }

    private static boolean isHoldingWrench(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && CloudFrameContent.getWrench() != null
            && stack.isOf(CloudFrameContent.getWrench());
    }

    private static boolean isProbeTarget(BlockState state) {
        if (state == null) return false;
        if (CloudFrameContent.getCloudCableBlock() != null && state.isOf(CloudFrameContent.getCloudCableBlock())) return true;
        if (CloudFrameContent.getStratusPanelBlock() != null && state.isOf(CloudFrameContent.getStratusPanelBlock())) return true;
        return CloudFrameContent.getCloudTurbineBlock() != null && state.isOf(CloudFrameContent.getCloudTurbineBlock());
    }

    private static void clearActionbarIfNeeded(MinecraftClient client) {
        if (!wasShowing) return;
        client.player.sendMessage(Text.empty(), true);
        wasShowing = false;
    }

    private static void resetState() {
        lastTargetPosLong = Long.MIN_VALUE;
        requestCooldownTicks = 0;
        lastResponsePosLong = Long.MIN_VALUE;
        lastResponseText = null;
    }

    private static Text format(int type, long produced, long stored, boolean externalApiPresent, int externalEndpointCount, long externalStored, long externalCapacity) {
        return switch (type) {
            case PowerProbePackets.TYPE_CABLE -> Text.literal(
                "Cloud Network: +" + formatNumber(produced) + " CFE/t | Stored: " + formatNumber(stored) + " CFE" + formatExternal(externalApiPresent, externalEndpointCount, externalStored, externalCapacity)
            );
            case PowerProbePackets.TYPE_STRATUS_PANEL -> Text.literal(
                "Stratus Panel: +" + formatNumber(produced) + " CFE/t"
            );
            case PowerProbePackets.TYPE_CLOUD_TURBINE -> Text.literal(
                "Cloud Turbine: +" + formatNumber(produced) + " CFE/t"
            );
            default -> null;
        };
    }

    private static String formatExternal(boolean externalApiPresent, int externalEndpointCount, long externalStored, long externalCapacity) {
        if (!externalApiPresent) return "";

        if (externalEndpointCount <= 0) {
            return " | External: none";
        }

        String extra = " | External: " + externalEndpointCount;
        if (externalCapacity > 0L) {
            extra += " (" + formatNumber(externalStored) + "/" + formatNumber(externalCapacity) + " CFE)";
        }
        return extra;
    }

    private static String formatNumber(long value) {
        return String.format("%,d", Math.max(0L, value));
    }
}
