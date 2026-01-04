package dev.cloudframe.fabric.listeners;

import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.fabric.CloudFrameFabric;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Protects glass frame blocks from being broken while a quarry is active.
 * Only allows breaking after the quarry controller has been removed.
 * Prevents silk touch drops when breaking frame glass.
 */
public class GlassFrameProtectionListener implements PlayerBlockBreakEvents.Before {

    @Override
    public boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null || instance.getQuarryManager() == null) return true;

        // Only care about glass blocks
        if (!state.isOf(Blocks.GLASS)) return true;

        // Check if this glass is part of any quarry's frame
        for (Quarry q : instance.getQuarryManager().all()) {
            if (isInQuarryFrame(pos, q)) {
                // Block is part of a quarry frame - prevent breaking
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(net.minecraft.text.Text.literal("§cThis glass is part of a quarry frame. Remove the controller to remove the frame."), true);
                }
                return false; // Cancel the break
            }
        }

        return true; // Not part of any quarry frame, allow breaking normally
    }

    private boolean isInQuarryFrame(BlockPos pos, Quarry quarry) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int minX = quarry.frameMinX();
        int maxX = quarry.frameMaxX();
        int maxY = quarry.getRegion().maxY();
        int minZ = quarry.frameMinZ();
        int maxZ = quarry.frameMaxZ();

        // Frame is a 2D ring on the top layer of the region.
        if (y != maxY) {
            return false;
        }

        // Check if position is within region bounds
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }

        // Check if it's on the perimeter ring
        return (x == minX || x == maxX || z == minZ || z == maxZ);
    }

    /**
     * Register this listener with Fabric API.
     */
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(new GlassFrameProtectionListener());
    }
}
