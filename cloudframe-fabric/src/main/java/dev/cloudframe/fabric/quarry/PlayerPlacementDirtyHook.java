package dev.cloudframe.fabric.quarry;

import java.util.concurrent.ConcurrentLinkedQueue;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tracks player block placements and marks those blocks dirty for active-scanning.
 *
 * This avoids mixin/refmap fragility in hybrid server environments by using Fabric API
 * callbacks plus a lightweight end-of-tick verification.
 */
public final class PlayerPlacementDirtyHook {

    private PlayerPlacementDirtyHook() {}

    private static final int MAX_CANDIDATES_PER_TICK = 512;

    private static final ConcurrentLinkedQueue<Candidate> CANDIDATES = new ConcurrentLinkedQueue<>();

    private record Candidate(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState before) {}

    public static void register() {
        UseBlockCallback.EVENT.register(PlayerPlacementDirtyHook::onUseBlock);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        ItemStack stack = serverPlayer.getStackInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) return ActionResult.PASS;

        // Try to compute the actual placement position using vanilla placement context logic.
        BlockPos placementPos;
        try {
            ItemPlacementContext ctx = new ItemPlacementContext(serverPlayer, hand, stack, hitResult);
            placementPos = ctx.getBlockPos();
        } catch (Throwable ignored) {
            // Fallback: assume adjacent placement.
            placementPos = hitResult.getBlockPos().offset(hitResult.getSide());
        }

        if (placementPos == null) return ActionResult.PASS;

        // Capture the pre-placement state; we'll verify at end-of-tick whether it changed.
        var before = serverWorld.getBlockState(placementPos);
        CANDIDATES.add(new Candidate(serverWorld, placementPos.toImmutable(), before));
        return ActionResult.PASS;
    }

    public static void tick() {
        var fabric = CloudFrameFabric.instance();
        if (fabric == null) return;
        var quarryManager = fabric.getQuarryManager();
        if (quarryManager == null) {
            // Server is not fully started yet; just drain to avoid unbounded growth.
            CANDIDATES.clear();
            return;
        }

        int processed = 0;
        while (processed < MAX_CANDIDATES_PER_TICK) {
            Candidate c = CANDIDATES.poll();
            if (c == null) break;
            processed++;

            // The world might have unloaded between callback and tick, so guard lightly.
            ServerWorld world = c.world();
            BlockPos pos = c.pos();
            if (world == null || pos == null) continue;

            var after = world.getBlockState(pos);
            if (after == null) continue;

            // Only mark dirty if something actually changed and the result is not air.
            if (after.equals(c.before())) continue;
            if (after.isAir()) continue;

            quarryManager.markDirtyBlock(world, pos.getX(), pos.getY(), pos.getZ());
        }

        // If we hit the cap, keep remaining candidates for next tick.
    }
}
