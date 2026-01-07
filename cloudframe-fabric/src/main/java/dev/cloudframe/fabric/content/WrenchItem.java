package dev.cloudframe.fabric.content;

import dev.cloudframe.common.quarry.Quarry;
import dev.cloudframe.common.util.Region;
import dev.cloudframe.fabric.CloudFrameFabric;
import dev.cloudframe.fabric.quarry.controller.QuarryControllerBlockEntity;
import dev.cloudframe.fabric.power.EnergyInterop;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.BlockState;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.Vec3d;
import dev.cloudframe.fabric.pipes.filter.PipeFilterItem;
import dev.cloudframe.common.pipes.connections.PipeKey;

public class WrenchItem extends Item {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        BlockPos clickedPos = context.getBlockPos();
        CloudFrameFabric instance = CloudFrameFabric.instance();
        if (instance == null) {
            return ActionResult.PASS;
        }

        if (!(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.PASS;
        }

        // Check if clicking on a cloud pipe
        if (CloudFrameContent.getTubeBlock() != null && context.getWorld().getBlockState(clickedPos).isOf(CloudFrameContent.getTubeBlock())) {
            if (instance.getPipeManager() == null) return ActionResult.PASS;

            if (instance.getPipeConnectionService() == null) return ActionResult.PASS;

            GlobalPos pipeLoc = GlobalPos.create(world.getRegistryKey(), clickedPos.toImmutable());

            var pipeNode = instance.getPipeManager().getPipe(pipeLoc);
            if (pipeNode == null) {
                // If the pipe cache is missing this placement, lazily index it.
                instance.getPipeManager().addPipe(pipeLoc);
                pipeNode = instance.getPipeManager().getPipe(pipeLoc);
            }
            if (pipeNode == null) return ActionResult.PASS;

            // Determine which direction was clicked.
            // Prefer the actual arm that was hit (so you can click the connection nub),
            // falling back to the hit face for center/core clicks.
            Direction side = getClickedTubeSide(context, serverPlayer);
            int dirIndex = toDirIndex(side);
            if (dirIndex < 0) return ActionResult.PASS;

            // Only allow toggling for external connections (inventories or quarry controller),
            // so players don't accidentally "disable" pipe-to-pipe adjacency.
            BlockPos neighborPos = clickedPos.offset(side);
            BlockState neighborState = world.getBlockState(neighborPos);

            boolean toggleable = false;
            if (CloudFrameContent.getTubeBlock() != null && neighborState.isOf(CloudFrameContent.getTubeBlock())) {
                // Allow disabling pipe-to-pipe adjacency so separate networks can run side-by-side.
                toggleable = true;
            } else if (CloudFrameContent.getQuarryControllerBlock() != null && neighborState.isOf(CloudFrameContent.getQuarryControllerBlock())) {
                toggleable = true;
            } else if (neighborState.hasBlockEntity()) {
                var be = world.getBlockEntity(neighborPos);
                toggleable = be instanceof Inventory;
            }

            if (!toggleable) {
                serverPlayer.sendMessage(Text.literal("§7No toggleable connection on that side."), true);
                return ActionResult.SUCCESS;
            }

            PipeKey key = new PipeKey(
                world.getRegistryKey().getValue().toString(),
                clickedPos.getX(),
                clickedPos.getY(),
                clickedPos.getZ()
            );

            boolean wasDisabled = instance.getPipeConnectionService().isSideDisabled(key, dirIndex);
            instance.getPipeConnectionService().toggleSide(key, dirIndex);
            int mask = instance.getPipeConnectionService().getDisabledSidesMask(key);
            pipeNode.setDisabledInventorySides(mask);
            boolean nowDisabled = instance.getPipeConnectionService().isSideDisabled(key, dirIndex);

            // If toggling a pipe-to-pipe connection, also toggle the opposite side on the neighbor
            if (CloudFrameContent.getTubeBlock() != null && neighborState != null && neighborState.isOf(CloudFrameContent.getTubeBlock())) {
                int oppositeDir = getOppositeDirectionIndex(dirIndex);
                PipeKey neighborKey = new PipeKey(
                    world.getRegistryKey().getValue().toString(),
                    neighborPos.getX(),
                    neighborPos.getY(),
                    neighborPos.getZ()
                );
                instance.getPipeConnectionService().toggleSide(neighborKey, oppositeDir);
                
                // Update neighbor pipe visuals
                var neighborPipeNode = instance.getPipeManager().getPipe(GlobalPos.create(world.getRegistryKey(), neighborPos.toImmutable()));
                if (neighborPipeNode != null) {
                    int neighborMask = instance.getPipeConnectionService().getDisabledSidesMask(neighborKey);
                    neighborPipeNode.setDisabledInventorySides(neighborMask);
                }
            }

            // If a wrench DISCONNECTS (disables) a side and it had a filter, drop the filter.
            if (!wasDisabled && nowDisabled && instance.getPipeFilterManager() != null) {
                if (instance.getPipeFilterManager().hasFilter(pipeLoc, dirIndex)) {
                    var st = instance.getPipeFilterManager().get(pipeLoc, dirIndex);
                    instance.getPipeFilterManager().removeFilter(pipeLoc, dirIndex);

                    ItemStack drop = new ItemStack(CloudFrameContent.getPipeFilter(), 1);
                    PipeFilterItem.writeItemConfigFromFilterState(drop, st);
                    serverPlayer.getInventory().insertStack(drop);
                    if (!drop.isEmpty()) {
                        ItemScatterer.spawn(world, serverPlayer.getX(), serverPlayer.getY() + 0.5, serverPlayer.getZ(), drop);
                    }
                }
            }
            
            // Rebuild pipe network to apply changes immediately
            instance.getPipeManager().rebuildAll();

            // Update the tube's connection arms immediately (server -> client sync via blockstate).
            TubeBlock.refreshConnections(world, clickedPos);
            if (CloudFrameContent.getTubeBlock() != null && neighborState != null && neighborState.isOf(CloudFrameContent.getTubeBlock())) {
                TubeBlock.refreshConnections(world, neighborPos);
            }
            
            // Give player feedback
            boolean disabled = instance.getPipeConnectionService().isSideDisabled(key, dirIndex);
            String dirName = switch (dirIndex) {
                case 0 -> "East";
                case 1 -> "West";
                case 2 -> "Up";
                case 3 -> "Down";
                case 4 -> "South";
                case 5 -> "North";
                default -> "Unknown";
            };
            serverPlayer.sendMessage(Text.literal(
                "§7Cloud Pipe connection §f" + dirName + "§7: " + (disabled ? "§cDisabled" : "§aEnabled")
            ), true);
            
            return ActionResult.SUCCESS;
        }

        // Check if clicking on a cloud cable
        if (CloudFrameContent.getCloudCableBlock() != null && context.getWorld().getBlockState(clickedPos).isOf(CloudFrameContent.getCloudCableBlock())) {
            var cableMgr = instance.getCableConnectionManager();
            if (cableMgr == null) return ActionResult.PASS;

            // Determine which direction was clicked (prefer arm hit).
            Direction side = getClickedTubeSide(context, serverPlayer);
            int dirIndex = toDirIndex(side);
            if (dirIndex < 0) return ActionResult.PASS;

            // Only allow toggling for external connections (not cable-to-cable).
            BlockPos neighborPos = clickedPos.offset(side);
            BlockState neighborState = world.getBlockState(neighborPos);

            boolean toggleable = false;
            if (neighborState == null || neighborState.isAir()) {
                toggleable = false;
            } else if (CloudFrameContent.getCloudCableBlock() != null && neighborState.isOf(CloudFrameContent.getCloudCableBlock())) {
                // Allow disabling cable-to-cable adjacency so separate networks can run side-by-side.
                toggleable = true;
            } else if (CloudFrameContent.getQuarryControllerBlock() != null && neighborState.isOf(CloudFrameContent.getQuarryControllerBlock())) {
                toggleable = true;
            } else if (CloudFrameContent.getStratusPanelBlock() != null && neighborState.isOf(CloudFrameContent.getStratusPanelBlock())) {
                toggleable = true;
            } else if (CloudFrameContent.getCloudTurbineBlock() != null && neighborState.isOf(CloudFrameContent.getCloudTurbineBlock())) {
                toggleable = true;
            } else if (CloudFrameContent.getCloudCellBlock() != null && neighborState.isOf(CloudFrameContent.getCloudCellBlock())) {
                toggleable = true;
            } else if (EnergyInterop.isAvailable()) {
                // Only allow toggling for external endpoints if the API reports an actual energy storage.
                var info = EnergyInterop.tryMeasureExternalCfe(world, neighborPos, side.getOpposite());
                toggleable = info != null;
            }

            if (!toggleable) {
                serverPlayer.sendMessage(Text.literal("§7No toggleable connection on that side."), true);
                return ActionResult.SUCCESS;
            }

            GlobalPos cablePos = GlobalPos.create(world.getRegistryKey(), clickedPos.toImmutable());
            cableMgr.toggleSide(cablePos, dirIndex);

            // If toggling a cable-to-cable connection, also toggle the opposite side on the neighbor
            if (CloudFrameContent.getCloudCableBlock() != null && neighborState != null && neighborState.isOf(CloudFrameContent.getCloudCableBlock())) {
                int oppositeDir = getOppositeDirectionIndex(dirIndex);
                GlobalPos neighborCablePos = GlobalPos.create(world.getRegistryKey(), neighborPos.toImmutable());
                cableMgr.toggleSide(neighborCablePos, oppositeDir);
            }

            // Update cable connection arms immediately (server -> client sync via blockstate).
            CloudCableBlock.refreshConnections(world, clickedPos);
            if (CloudFrameContent.getCloudCableBlock() != null && neighborState != null && neighborState.isOf(CloudFrameContent.getCloudCableBlock())) {
                CloudCableBlock.refreshConnections(world, neighborPos);
            }

            boolean disabled = cableMgr.isSideDisabled(cablePos, dirIndex);
            String dirName = switch (dirIndex) {
                case 0 -> "East";
                case 1 -> "West";
                case 2 -> "Up";
                case 3 -> "Down";
                case 4 -> "South";
                case 5 -> "North";
                default -> "Unknown";
            };
            serverPlayer.sendMessage(Text.literal(
                "§7Cloud Cable connection §f" + dirName + "§7: " + (disabled ? "§cDisabled" : "§aEnabled")
            ), true);

            return ActionResult.SUCCESS;
        }

        // Wrench activation of marker frames is handled by FabricWrenchMarkerActivationListener
        // Controller placement is handled by QuarryControllerBlock
        return ActionResult.PASS;
    }

    private static int toDirIndex(Direction side) {
        return switch (side) {
            case EAST -> 0;   // +X
            case WEST -> 1;   // -X
            case UP -> 2;     // +Y
            case DOWN -> 3;   // -Y
            case SOUTH -> 4;  // +Z
            case NORTH -> 5;  // -Z
        };
    }

    /**
     * Returns the opposite direction index.
     * East (0) <-> West (1), Up (2) <-> Down (3), South (4) <-> North (5)
     */
    private static int getOppositeDirectionIndex(int dirIndex) {
        return switch (dirIndex) {
            case 0 -> 1;  // East -> West
            case 1 -> 0;  // West -> East
            case 2 -> 3;  // Up -> Down
            case 3 -> 2;  // Down -> Up
            case 4 -> 5;  // South -> North
            case 5 -> 4;  // North -> South
            default -> dirIndex;
        };
    }

    private static Direction getClickedTubeSide(ItemUsageContext context, ServerPlayerEntity player) {
        Vec3d hitPos = null;
        if (player != null) {
            HitResult ray = player.raycast(5.0D, 0.0F, false);
            if (ray instanceof BlockHitResult bhr && bhr.getBlockPos().equals(context.getBlockPos())) {
                hitPos = bhr.getPos();
            }
        }

        if (hitPos == null) {
            return context.getSide();
        }

        BlockPos blockPos = context.getBlockPos();

        double localX = hitPos.x - blockPos.getX();
        double localY = hitPos.y - blockPos.getY();
        double localZ = hitPos.z - blockPos.getZ();

        // Tube arm bounds match TubeBlock voxel shapes:
        // core is 6/16..10/16 on all axes; arms extend to 0..6/16 or 10/16..16/16.
        final double coreMin = 6.0 / 16.0;
        final double coreMax = 10.0 / 16.0;

        boolean inCoreY = localY >= coreMin && localY <= coreMax;
        boolean inCoreZ = localZ >= coreMin && localZ <= coreMax;
        boolean inCoreX = localX >= coreMin && localX <= coreMax;

        // If you're actually pointing at an arm volume, use that direction.
        if (inCoreY && inCoreZ) {
            if (localX < coreMin) return Direction.WEST;
            if (localX > coreMax) return Direction.EAST;
        }
        if (inCoreX && inCoreY) {
            if (localZ < coreMin) return Direction.NORTH;
            if (localZ > coreMax) return Direction.SOUTH;
        }
        if (inCoreX && inCoreZ) {
            if (localY < coreMin) return Direction.DOWN;
            if (localY > coreMax) return Direction.UP;
        }

        // Otherwise fall back to the face Minecraft reports.
        return context.getSide();
    }
}
