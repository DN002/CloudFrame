package dev.cloudframe.fabric.listeners;

import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Handles quarry controller block breaks to properly deregister quarries.
 * Prevents orphaned quarries from continuing to function after controller removal.
 */
public class QuarryControllerBreakListener implements PlayerBlockBreakEvents.Before {

    @Override
    public boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getQuarryManager() == null) return true;

        // Only care about quarry controller blocks
        if (!state.isOf(CloudFrameContent.getQuarryControllerBlock())) return true;

        // Find the quarry associated with this controller
        Quarry quarry = instance.getQuarryManager().getByController(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
        if (quarry == null) {
            // Controller exists but no registered quarry - allow break
            return true;
        }

        // Remove the glass frame first
        quarry.removeGlassFrame();

        // Deregister the quarry
        instance.getQuarryManager().remove(quarry);
        instance.getQuarryManager().saveAll();

        if (player instanceof ServerPlayerEntity sp) {
            sp.sendMessage(net.minecraft.text.Text.literal("§cQuarry deregistered and removed."), true);
        }

        // Allow the block break to proceed
        return true;
    }

    /**
     * Register this listener with Fabric API.
     */
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(new QuarryControllerBreakListener());
    }
}
