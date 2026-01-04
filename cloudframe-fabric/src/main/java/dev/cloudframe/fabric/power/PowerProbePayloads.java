package dev.cloudframe.fabric.power;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PowerProbePayloads {

    private PowerProbePayloads() {
    }

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        PayloadTypeRegistry.playC2S().register(PowerProbeRequestPayload.ID, PowerProbeRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PowerProbeResponsePayload.ID, PowerProbeResponsePayload.CODEC);
    }
}
