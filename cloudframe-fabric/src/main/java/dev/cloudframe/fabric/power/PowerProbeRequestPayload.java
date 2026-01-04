package dev.cloudframe.fabric.power;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PowerProbeRequestPayload(long posLong) implements CustomPayload {

    public static final CustomPayload.Id<PowerProbeRequestPayload> ID = new CustomPayload.Id<>(
        Identifier.of(CloudFrameFabric.MOD_ID, "power_probe_request")
    );

    public static final PacketCodec<RegistryByteBuf, PowerProbeRequestPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.LONG,
        PowerProbeRequestPayload::posLong,
        PowerProbeRequestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
