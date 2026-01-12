package dev.cloudframe.fabric.listeners;

import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.fabric.wrench.WrenchInteractionUtil;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.DebugFlags;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class FabricWrenchRotateIoListener {

    private FabricWrenchRotateIoListener() {
    }

    public static void register() {
        Debug debug = DebugManager.get(FabricWrenchRotateIoListener.class);
        debug.log("register", "Registering wrench shift-right-click rotation listener (" + dev.cloudframe.fabric.CloudFrameFabric.BUILD_STAMP + ")");
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack usedHand = player.getStackInHand(hand);
            boolean usedIsWrench = usedHand != null && !usedHand.isEmpty() && usedHand.isOf(CloudFrameContent.WRENCH);
            boolean usedIsEmpty = usedHand == null || usedHand.isEmpty();

            // Important: the client often "uses" MAIN_HAND first even if it's empty,
            // which means OFF_HAND wrench never gets an interaction attempt.
            // Allow interception when the used hand is empty but the other hand holds the wrench.
            ItemStack otherHand = (hand == net.minecraft.util.Hand.MAIN_HAND) ? player.getOffHandStack() : player.getMainHandStack();
            boolean otherIsWrench = otherHand != null && !otherHand.isEmpty() && otherHand.isOf(CloudFrameContent.WRENCH);

            // If the player is crouching, log what the server thinks is in each hand.
            // This makes it obvious when the callback isn't firing at all vs. not recognizing the wrench.
            boolean isCrouching = player.isSneaking() || player.isInSneakingPose();
            if (DebugFlags.WRENCH_USE_LOGGING && isCrouching) {
                var usedId = (usedHand == null || usedHand.isEmpty()) ? "<empty>" : String.valueOf(Registries.ITEM.getId(usedHand.getItem()));
                var otherId = (otherHand == null || otherHand.isEmpty()) ? "<empty>" : String.valueOf(Registries.ITEM.getId(otherHand.getItem()));
                var blockId = String.valueOf(Registries.BLOCK.getId(world.getBlockState(hitResult.getBlockPos()).getBlock()));
                debug.log("use", "WrenchUseBlockCallback crouch=true hand=" + hand + " used=" + usedId + " other=" + otherId + " block=" + blockId);
            }

            if (!(usedIsWrench || (usedIsEmpty && otherIsWrench))) return ActionResult.PASS;

            var pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (state == null) return ActionResult.PASS;

            // Don't interfere with marker activation or the wrench's existing tube/cable logic.
            if (state.isOf(CloudFrameContent.MARKER_BLOCK)) return ActionResult.PASS;
            if (state.isOf(CloudFrameContent.CLOUD_PIPE_BLOCK) || state.isOf(CloudFrameContent.CLOUD_CABLE_BLOCK)) return ActionResult.PASS;
            if (state.isOf(CloudFrameContent.QUARRY_CONTROLLER_BLOCK)) return ActionResult.PASS;

            // Only intercept sneak-use.
            // Normal right-click should behave like vanilla (open GUIs, toggle doors, etc).
            if (!isCrouching) return ActionResult.PASS;

            if (WrenchInteractionUtil.tryRotate(serverWorld, pos, serverPlayer)) {
                serverPlayer.sendMessage(Text.literal("§7Rotated."), true);
                return ActionResult.SUCCESS;
            }

            // Debug breadcrumb when rotation fails.
            var id = Registries.BLOCK.getId(state.getBlock());
            serverPlayer.sendMessage(Text.literal("§7Wrench: can't rotate §f" + id + "§7 (crouch=" + isCrouching + ")"), true);

            return ActionResult.PASS;
        });
    }
}
