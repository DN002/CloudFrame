package dev.cloudframe.fabric.wrench;

import dev.cloudframe.fabric.CloudFrameFabric;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class WrenchInteractionUtil {

    private WrenchInteractionUtil() {
    }

    public static boolean tryRotate(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        if (world == null || pos == null || player == null) return false;
        BlockState state = world.getBlockState(pos);
        if (state == null) return false;

        // Rails are neighbor/shape-driven and don't rotate predictably.
        if (state.contains(Properties.RAIL_SHAPE) || state.getBlock() instanceof AbstractRailBlock) {
            return false;
        }

        Identifier id = Registries.BLOCK.getId(state.getBlock());
        CloudFrameFabric instance = CloudFrameFabric.instance();
        var cfg = (instance != null) ? instance.getWrenchConfig() : null;
        if (cfg != null && !cfg.isRotationAllowed(id)) {
            return false;
        }

        // Special cases: multi-block and adjacency-sensitive blocks.
        if (state.getBlock() instanceof DoorBlock) {
            return rotateDoor(world, pos, state);
        }

        // Chest rotation.
        if (state.getBlock() instanceof ChestBlock && state.contains(Properties.CHEST_TYPE) && state.contains(Properties.HORIZONTAL_FACING)) {
            return rotateChest(world, pos, state);
        }

        // Prefer vanilla block rotation hooks.
        BlockState rotated = state;
        try {
            rotated = state.rotate(BlockRotation.CLOCKWISE_90);
        } catch (Throwable ignored) {
            rotated = state;
        }

        // Fallback for common properties.
        if (rotated == state) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                var cur = state.get(Properties.HORIZONTAL_FACING);
                rotated = state.with(Properties.HORIZONTAL_FACING, cur.rotateYClockwise());
            } else if (state.contains(Properties.AXIS)) {
                var cur = state.get(Properties.AXIS);
                var next = switch (cur) {
                    case X -> Direction.Axis.Y;
                    case Y -> Direction.Axis.Z;
                    case Z -> Direction.Axis.X;
                };
                rotated = state.with(Properties.AXIS, next);
            } else if (state.contains(Properties.HORIZONTAL_AXIS)) {
                var cur = state.get(Properties.HORIZONTAL_AXIS);
                var next = (cur == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
                rotated = state.with(Properties.HORIZONTAL_AXIS, next);
            } else {
                return false;
            }
        }

        if (rotated == state) return false;

        world.setBlockState(pos, rotated, Block.NOTIFY_ALL);
        return true;
    }

    private static boolean rotateDoor(ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.HORIZONTAL_FACING) || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        var facing = state.get(Properties.HORIZONTAL_FACING);
        var newFacing = facing.rotateYClockwise();

        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
        BlockState other = world.getBlockState(otherPos);
        if (other == null || other.getBlock() != state.getBlock()) {
            BlockState rotated = state.with(Properties.HORIZONTAL_FACING, newFacing);
            if (rotated == state) return false;
            world.setBlockState(pos, rotated, Block.NOTIFY_ALL);
            return true;
        }

        if (!other.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        BlockState rotatedA = state.with(Properties.HORIZONTAL_FACING, newFacing);
        BlockState rotatedB = other.with(Properties.HORIZONTAL_FACING, newFacing);
        if (rotatedA == state && rotatedB == other) return false;

        world.setBlockState(pos, rotatedA, Block.NOTIFY_ALL);
        world.setBlockState(otherPos, rotatedB, Block.NOTIFY_ALL);
        return true;
    }

    private static boolean rotateChest(ServerWorld world, BlockPos pos, BlockState state) {
        ChestType type = state.get(Properties.CHEST_TYPE);
        var facing = state.get(Properties.HORIZONTAL_FACING);

        if (type != ChestType.SINGLE) {
            Direction offsetToOther = (type == ChestType.LEFT) ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
            BlockPos otherPos = pos.offset(offsetToOther);
            BlockState other = world.getBlockState(otherPos);
            if (other == null || other.getBlock() != state.getBlock()) {
                return rotateChestSingle(world, pos, state);
            }
            if (!other.contains(Properties.CHEST_TYPE) || !other.contains(Properties.HORIZONTAL_FACING)) {
                return rotateChestSingle(world, pos, state);
            }
            if (other.get(Properties.CHEST_TYPE) == ChestType.SINGLE) {
                return rotateChestSingle(world, pos, state);
            }

            var newFacing = facing.getOpposite();
            ChestType swapped = (type == ChestType.LEFT) ? ChestType.RIGHT : ChestType.LEFT;
            ChestType otherType = other.get(Properties.CHEST_TYPE);
            ChestType otherSwapped = (otherType == ChestType.LEFT) ? ChestType.RIGHT : ChestType.LEFT;

            BlockState rotatedA = state.with(Properties.HORIZONTAL_FACING, newFacing).with(Properties.CHEST_TYPE, swapped);
            BlockState rotatedB = other.with(Properties.HORIZONTAL_FACING, newFacing).with(Properties.CHEST_TYPE, otherSwapped);

            world.setBlockState(pos, rotatedA, Block.NOTIFY_ALL);
            world.setBlockState(otherPos, rotatedB, Block.NOTIFY_ALL);
            return true;
        }

        return rotateChestSingle(world, pos, state);
    }

    private static boolean rotateChestSingle(ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.contains(Properties.HORIZONTAL_FACING) || !state.contains(Properties.CHEST_TYPE)) return false;

        var facing = state.get(Properties.HORIZONTAL_FACING);
        var newFacing = facing.rotateYClockwise();

        BlockState rotated = state.with(Properties.HORIZONTAL_FACING, newFacing).with(Properties.CHEST_TYPE, ChestType.SINGLE);
        world.setBlockState(pos, rotated, Block.NOTIFY_ALL);

        Direction leftDir = newFacing.rotateYCounterclockwise();
        Direction rightDir = newFacing.rotateYClockwise();

        if (tryConnectChest(world, pos, rotated, pos.offset(leftDir), true)) {
            return true;
        }
        if (tryConnectChest(world, pos, rotated, pos.offset(rightDir), false)) {
            return true;
        }

        return true;
    }

    private static boolean tryConnectChest(ServerWorld world, BlockPos selfPos, BlockState selfState, BlockPos neighborPos, boolean neighborIsLeft) {
        BlockState neighbor = world.getBlockState(neighborPos);
        if (neighbor == null) return false;
        if (neighbor.getBlock() != selfState.getBlock()) return false;
        if (!neighbor.contains(Properties.CHEST_TYPE) || !neighbor.contains(Properties.HORIZONTAL_FACING)) return false;
        if (neighbor.get(Properties.CHEST_TYPE) != ChestType.SINGLE) return false;
        if (neighbor.get(Properties.HORIZONTAL_FACING) != selfState.get(Properties.HORIZONTAL_FACING)) return false;

        ChestType selfType = neighborIsLeft ? ChestType.RIGHT : ChestType.LEFT;
        ChestType neighborType = neighborIsLeft ? ChestType.LEFT : ChestType.RIGHT;

        BlockState newSelf = selfState.with(Properties.CHEST_TYPE, selfType);
        BlockState newNeighbor = neighbor.with(Properties.CHEST_TYPE, neighborType);
        world.setBlockState(selfPos, newSelf, Block.NOTIFY_ALL);
        world.setBlockState(neighborPos, newNeighbor, Block.NOTIFY_ALL);
        return true;
    }
}
