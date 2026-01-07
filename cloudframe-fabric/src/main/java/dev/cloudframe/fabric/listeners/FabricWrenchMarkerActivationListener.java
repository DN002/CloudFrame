package dev.cloudframe.fabric.listeners;

import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles wrench right-click to activate marker frames.
 * Scans nearby blocks for placed markers (4 required on same Y level).
 * Draws red particle lines connecting the corners in a rectangle.
 */
public class FabricWrenchMarkerActivationListener {

    // Red color for particle lines (as integer: 0xFF0000)
    private static final int RED_COLOR_INT = 0xFF0000;
    private static final int SEARCH_RADIUS = 16; // Search 16 blocks in each direction

    public static void register() {
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
            if (item.getItem() != CloudFrameContent.WRENCH) {
                return ActionResult.PASS;
            }

            // Let the quarry controller block handle wrench interactions (finalize/register).
            // If we return SUCCESS here, Fabric will not call the block's onUse.
            BlockPos clickPos = hitResult.getBlockPos();
            BlockState clickedState = ((ServerWorld) world).getBlockState(clickPos);
            if (clickedState.getBlock() == CloudFrameContent.QUARRY_CONTROLLER_BLOCK) {
                return ActionResult.PASS;
            }

            // Let the wrench's pipe-connection toggle logic handle tube blocks.
            if (CloudFrameContent.getTubeBlock() != null && clickedState.isOf(CloudFrameContent.getTubeBlock())) {
                return ActionResult.PASS;
            }

            // Let the wrench's cable-connection toggle logic handle cloud cables.
            if (CloudFrameContent.getCloudCableBlock() != null && clickedState.isOf(CloudFrameContent.getCloudCableBlock())) {
                return ActionResult.PASS;
            }

            // Scan nearby blocks for markers
            List<BlockPos> foundMarkers = scanForMarkers((ServerWorld) world, clickPos);

            if (foundMarkers.size() < 4) {
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§cNeed 4 markers placed. Found: " + foundMarkers.size()),
                    false
                );
                return ActionResult.SUCCESS;
            }

            if (foundMarkers.size() > 4) {
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§cToo many markers found (" + foundMarkers.size() + "). Remove extras."),
                    false
                );
                return ActionResult.SUCCESS;
            }

            // Validate all on same Y level
            int firstY = foundMarkers.get(0).getY();
            for (BlockPos marker : foundMarkers) {
                if (marker.getY() != firstY) {
                    serverPlayer.sendMessage(
                        net.minecraft.text.Text.literal("§cMarkers must be on same Y level."),
                        false
                    );
                    return ActionResult.SUCCESS;
                }
            }

            var manager = CloudFrameFabric.instance().getMarkerManager();
            
            if (manager.isActivated(serverPlayer.getUuid())) {
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§eFrame already activated."),
                    false
                );
                return ActionResult.SUCCESS;
            }

            // Activate the frame
            manager.setFrameFromBlocks(serverPlayer.getUuid(), (ServerWorld) world, foundMarkers);

            // Draw red particle lines connecting corners
            drawFrameLines((ServerWorld) world, foundMarkers);

                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("§aFrame activated! Place the controller adjacent to the frame on the same Y level as the markers."),
                    false
                );

            return ActionResult.SUCCESS;
        });
    }

    /**
     * Scans nearby blocks for marker blocks.
     */
    private static List<BlockPos> scanForMarkers(ServerWorld world, BlockPos center) {
        List<BlockPos> markers = new ArrayList<>();
        
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() == CloudFrameContent.MARKER_BLOCK) {
                        markers.add(pos);
                    }
                }
            }
        }
        
        return markers;
    }

    /**
     * Draw red particle lines connecting the 4 corners in a rectangle.
     */
    private static void drawFrameLines(ServerWorld world, List<BlockPos> corners) {
        if (corners.size() < 4) return;

        // Find bounds to determine rectangle corners
        BlockPos first = corners.get(0);
        int minX = first.getX();
        int maxX = first.getX();
        int minZ = first.getZ();
        int maxZ = first.getZ();
        int y = first.getY();

        for (BlockPos corner : corners) {
            minX = Math.min(minX, corner.getX());
            maxX = Math.max(maxX, corner.getX());
            minZ = Math.min(minZ, corner.getZ());
            maxZ = Math.max(maxZ, corner.getZ());
        }

        // Render the frame on the marker perimeter so it lines up with placed markers.

        // Define the 4 rectangle corners
        BlockPos c1 = new BlockPos(minX, y, minZ);
        BlockPos c2 = new BlockPos(maxX, y, minZ);
        BlockPos c3 = new BlockPos(maxX, y, maxZ);
        BlockPos c4 = new BlockPos(minX, y, maxZ);

        // Draw lines between corners (rectangle perimeter)
        drawLine(world, c1, c2);
        drawLine(world, c2, c3);
        drawLine(world, c3, c4);
        drawLine(world, c4, c1);
    }

    /**
     * Draw a red particle line between two positions.
     */
    private static void drawLine(ServerWorld world, BlockPos from, BlockPos to) {
        Vec3d start = Vec3d.ofCenter(from).add(0, 0.5, 0);
        Vec3d end = Vec3d.ofCenter(to).add(0, 0.5, 0);

        double distance = start.distanceTo(end);
        int particleCount = Math.max(8, (int) (distance * 4.0)); // ~4 particles per block

        for (int i = 0; i <= particleCount; i++) {
            double t = i / (double) particleCount;
            Vec3d pos = start.lerp(end, t);

            // Spawn red dust particles (server-side) so all nearby players see the frame.
            world.spawnParticles(
                new DustParticleEffect(RED_COLOR_INT, 0.65f),
                pos.x, pos.y, pos.z,
                1,
                0, 0, 0,
                0
            );
        }
    }
}
