package dev.cloudframe.fabric.quarry.controller;

import com.mojang.serialization.MapCodec;
import dev.cloudframe.common.markers.MarkerFrameCanonicalizer;
import dev.cloudframe.common.markers.MarkerPos;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.content.CloudFrameContent;
import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.common.quarry.QuarryFramePlanner;
import dev.cloudframe.common.util.Region;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

public class QuarryControllerBlock extends BlockWithEntity {

    public static final MapCodec<QuarryControllerBlock> CODEC = createCodec(QuarryControllerBlock::new);

    public QuarryControllerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world == null || world.isClient()) return null;
        if (type != CloudFrameContent.getQuarryControllerBlockEntity()) return null;

        return (w, p, s, be) -> {
            if (be instanceof QuarryControllerBlockEntity qbe) {
                QuarryControllerBlockEntity.tick(w, p, s, qbe);
            }
        };
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (world.getBlockEntity(pos) instanceof QuarryControllerBlockEntity be) {
            // If the player is holding the wrench, perform registration instead of opening the GUI.
            ItemStack inHand = player.getMainHandStack();
            if (inHand != null && !inHand.isEmpty() && inHand.isOf(CloudFrameContent.getWrench())) {
                if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                    return ActionResult.PASS;
                }
                CloudFrameFabric instance = CloudFrameFabric.instance();
                if (instance == null || instance.getQuarryManager() == null || instance.getMarkerManager() == null) {
                    return ActionResult.PASS;
                }
                if (!(world instanceof ServerWorld sw)) {
                    return ActionResult.PASS;
                }

                var controllerLoc = GlobalPos.create(sw.getRegistryKey(), pos.toImmutable());

                if (instance.getQuarryManager().getByController(controllerLoc) != null) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("This controller is already registered."), false);
                    return ActionResult.SUCCESS;
                }

                // Check if player has an activated frame (4 corners + wrench confirmed)
                if (!instance.getMarkerManager().isActivated(serverPlayer.getUuid())) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cPlace 4 corner markers on the same Y level, then activate with wrench first!"), false);
                    return ActionResult.SUCCESS;
                }

                // Get the frame corners
                var corners = instance.getMarkerManager().getCorners(serverPlayer.getUuid());
                if (corners.size() != 4) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cFrame incomplete. Place all 4 corners first."), false);
                    return ActionResult.SUCCESS;
                }

                // Delegate shared frame/controller validation + inner bounds computation to Common.
                java.util.List<MarkerPos> markerCorners = new java.util.ArrayList<>(4);
                for (BlockPos c : corners) {
                    if (c == null) continue;
                    markerCorners.add(new MarkerPos(c.getX(), c.getY(), c.getZ()));
                }

                QuarryFramePlanner.Result plan = QuarryFramePlanner.planFromMarkerCorners(
                    markerCorners,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                );

                if (!plan.ok()) {
                    if (plan.status == QuarryFramePlanner.Status.INVALID_FRAME && plan.frameStatus == MarkerFrameCanonicalizer.Status.TOO_SMALL) {
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cMarker frame is too small. Make it at least 3x3 (inside area must exist)."), false);
                    } else {
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cController must be placed adjacent to the frame on the same Y level as the markers."), false);
                    }
                    return ActionResult.SUCCESS;
                }

                int minX = plan.frameMinX;
                int maxX = plan.frameMaxX;
                int minZ = plan.frameMinZ;
                int maxZ = plan.frameMaxZ;
                int frameY = plan.frameY;

                int innerMinX = plan.innerMinX;
                int innerMaxX = plan.innerMaxX;
                int innerMinZ = plan.innerMinZ;
                int innerMaxZ = plan.innerMaxZ;

                // Create region from frame bounds (full vertical column)
                int topY = frameY;
                int bottomY = sw.getBottomY();

                // IMPORTANT: Persist the vertical bounds in posA/posB so that after restart
                // the quarry reloads with the full column (not a single Y slice).
                BlockPos a = new BlockPos(innerMinX, bottomY, innerMinZ);
                BlockPos b = new BlockPos(innerMaxX, topY, innerMaxZ);
                Region region = new Region(sw.getRegistryKey(), innerMinX, bottomY, innerMinZ, sw.getRegistryKey(), innerMaxX, topY, innerMaxZ);
                int yaw = Math.round(serverPlayer.getYaw());

                // Persist owner info both on the BE (for UI) and on the Quarry (for DB/manager state).
                be.setOwner(serverPlayer.getUuid());
                be.setOwnerName(serverPlayer.getName().getString());

                Quarry q = new Quarry(
                    serverPlayer.getUuid(),
                    serverPlayer.getName().getString(),
                    GlobalPos.create(sw.getRegistryKey(), a),
                    GlobalPos.create(sw.getRegistryKey(), b),
                    region,
                    controllerLoc,
                    yaw,
                    instance.getQuarryPlatform()
                );
                // Visual frame should match the marker perimeter (particles + glass), even though mining is inset.
                q.setFrameBounds(minX, minZ, maxX, maxZ);
                // Don't auto-start - player must toggle lever in GUI to begin mining
                q.setSilkTouchAugment(be.isSilkTouch());
                q.setSpeedAugmentLevel(be.getSpeedLevel());
                q.setFortuneAugmentLevel(be.getFortuneLevel());
                q.setOutputRoundRobin(be.isOutputRoundRobin());

                instance.getQuarryManager().register(q);

                // Remove the 4 marker blocks, then replace with glass.
                // We spawn the marker drops explicitly on top of the glass so they are visible.
                java.util.List<BlockPos> brokenMarkers = new java.util.ArrayList<>();
                for (BlockPos corner : corners) {
                    if (corner == null) continue;
                    if (sw.getBlockState(corner).getBlock() == CloudFrameContent.MARKER_BLOCK) {
                        sw.breakBlock(corner, false);
                        brokenMarkers.add(corner);
                    }
                }

                q.createGlassFrame();

                for (BlockPos corner : brokenMarkers) {
                    // Drop marker item above the frame glass block that replaced it.
                    ItemScatterer.spawn(
                        sw,
                        corner.getX() + 0.5,
                        corner.getY() + 1.1,
                        corner.getZ() + 0.5,
                        new ItemStack(CloudFrameContent.MARKER, 1)
                    );
                }

                instance.getQuarryManager().saveQuarry(q);
                instance.getMarkerManager().clearCorners(serverPlayer.getUuid());

                serverPlayer.sendMessage(net.minecraft.text.Text.literal("§aQuarry registered successfully!"), false);
                return ActionResult.SUCCESS;
            }

            player.openHandledScreen(be);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        if (world.getBlockEntity(pos) instanceof QuarryControllerBlockEntity be) {
            be.onBroken();
        }
    }
}
