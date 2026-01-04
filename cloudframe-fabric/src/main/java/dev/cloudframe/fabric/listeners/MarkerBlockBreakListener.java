package dev.cloudframe.fabric.listeners;

import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Deactivates any activated marker frame if one of its marker blocks is broken.
 */
public final class MarkerBlockBreakListener {

    private MarkerBlockBreakListener() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) -> {
            if (!(world instanceof ServerWorld sw)) return true;
            if (state == null || state.getBlock() != CloudFrameContent.MARKER_BLOCK) return true;

            var inst = CloudFrameFabric.instance();
            if (inst == null || inst.getMarkerManager() == null) return true;

            inst.getMarkerManager().onMarkerBroken(sw, pos);
            return true; // allow the break
        });
    }
}
