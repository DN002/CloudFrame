package dev.cloudframe.fabric.listeners;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import dev.cloudframe.fabric.CloudFrameFabric;

/**
 * Handles player interactions with the marker item.
 * Left-click = set position A
 * Right-click = set position B
 */
public class FabricMarkerListener {

    private static final int MARKER_CUSTOM_MODEL_DATA = 1002;
    private static final long DEBOUNCE_MS = 250;
    private static final java.util.Map<java.util.UUID, Long> lastClick = new java.util.HashMap<>();

    public static void register() {
        // RIGHT-CLICK on block
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.PASS;
            }

            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }

            var item = serverPlayer.getMainHandStack();
            if (!isMarkerItem(item)) {
                return ActionResult.PASS;
            }

            // Debounce
            long now = System.currentTimeMillis();
            long last = lastClick.getOrDefault(serverPlayer.getUuid(), 0L);
            if (now - last < DEBOUNCE_MS) {
                return ActionResult.FAIL;
            }
            lastClick.put(serverPlayer.getUuid(), now);

            System.out.println("[FabricMarkerListener] Player " + serverPlayer.getName().getString() + " right-clicked marker");

            var manager = CloudFrameFabric.instance().getMarkerManager();
            int nextCorner = manager.addCorner(serverPlayer, hitResult.getBlockPos());

            if (nextCorner > 0) {
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§bMarker Corner " + nextCorner + " set."),
                    false
                );
            } else {
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§cAll 4 corners must be on the same Y level. Markers reset."),
                    false
                );
            }

            return ActionResult.FAIL; // Prevent block interaction
        });

        // LEFT-CLICK on block (break detection)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }

            var item = serverPlayer.getMainHandStack();
            if (!isMarkerItem(item)) {
                return true;
            }

            // Debounce
            long now = System.currentTimeMillis();
            long last = lastClick.getOrDefault(serverPlayer.getUuid(), 0L);
            if (now - last < DEBOUNCE_MS) {
                return false; // Cancel
            }
            lastClick.put(serverPlayer.getUuid(), now);

            System.out.println("[FabricMarkerListener] Player " + serverPlayer.getName().getString() + " left-clicked marker");

            var manager = CloudFrameFabric.instance().getMarkerManager();
            manager.clearCorners(serverPlayer);

            serverPlayer.sendMessage(
                net.minecraft.text.Text.literal("§bMarkers cleared. Right-click to place 4 corners."),
                false
            );

            return false; // Cancel the break
        });
    }

    private static boolean isMarkerItem(net.minecraft.item.ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        // Check if item is the MARKER item (now a BlockItem that places MARKER_BLOCK)
        return item.getItem() == dev.cloudframe.fabric.content.CloudFrameContent.MARKER;
    }
}
