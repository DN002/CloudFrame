package dev.cloudframe.fabric.util;

import dev.cloudframe.common.util.DirIndex;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ClickSideUtil {

    private ClickSideUtil() {
    }

    public static int toDirIndex(Direction side) {
        return switch (side) {
            case EAST -> DirIndex.EAST;
            case WEST -> DirIndex.WEST;
            case UP -> DirIndex.UP;
            case DOWN -> DirIndex.DOWN;
            case SOUTH -> DirIndex.SOUTH;
            case NORTH -> DirIndex.NORTH;
        };
    }

    /**
     * Determines which connection arm was clicked on a tube/cable-style block.
     * Uses a server-side raycast for reliable hit position.
     */
    public static Direction getClickedArmSide(ServerPlayerEntity player, BlockPos blockPos, Direction fallbackFace) {
        Vec3d hitPos = null;
        if (player != null) {
            HitResult ray = player.raycast(5.0D, 0.0F, false);
            if (ray instanceof BlockHitResult bhr && bhr.getBlockPos().equals(blockPos)) {
                hitPos = bhr.getPos();
            }
        }

        if (hitPos == null) {
            return fallbackFace;
        }

        double localX = hitPos.x - blockPos.getX();
        double localY = hitPos.y - blockPos.getY();
        double localZ = hitPos.z - blockPos.getZ();

        final double coreMin = 6.0 / 16.0;
        final double coreMax = 10.0 / 16.0;

        boolean inCoreY = localY >= coreMin && localY <= coreMax;
        boolean inCoreZ = localZ >= coreMin && localZ <= coreMax;
        boolean inCoreX = localX >= coreMin && localX <= coreMax;

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

        return fallbackFace;
    }
}
