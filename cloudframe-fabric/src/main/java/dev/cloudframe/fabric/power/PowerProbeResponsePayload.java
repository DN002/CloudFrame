package dev.cloudframe.fabric.power;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PowerProbeResponsePayload(
    long posLong,
    int type,
    long produced,
    long stored,
    int externalApiPresentFlag,
    int externalEndpointCount,
    long externalStored,
    long externalCapacity
) implements CustomPayload {

    public static final CustomPayload.Id<PowerProbeResponsePayload> ID = new CustomPayload.Id<>(
        Identifier.of(CloudFrameFabric.MOD_ID, "power_probe_response")
    );

    public static final PacketCodec<RegistryByteBuf, PowerProbeResponsePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.LONG,
        PowerProbeResponsePayload::posLong,
        PacketCodecs.VAR_INT,
        PowerProbeResponsePayload::type,
        PacketCodecs.LONG,
        PowerProbeResponsePayload::produced,
        PacketCodecs.LONG,
        PowerProbeResponsePayload::stored,
        PacketCodecs.VAR_INT,
        PowerProbeResponsePayload::externalApiPresentFlag,
        PacketCodecs.VAR_INT,
        PowerProbeResponsePayload::externalEndpointCount,
        PacketCodecs.LONG,
        PowerProbeResponsePayload::externalStored,
        PacketCodecs.LONG,
        PowerProbeResponsePayload::externalCapacity,
        PowerProbeResponsePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
