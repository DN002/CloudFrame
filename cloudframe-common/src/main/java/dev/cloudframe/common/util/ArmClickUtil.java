package dev.cloudframe.common.util;

/**
 * Shared hit-test utility for tube/cable-style "arms".
 *
 * <p>Given a hit point in block-local coordinates (0..1 for each axis),
 * returns the arm direction index (see {@link DirIndex}) or the provided fallback.</p>
 */
public final class ArmClickUtil {

    private ArmClickUtil() {
    }

    // Matches the Fabric implementation: core is the central 4x4 pixels (6..10) in a 16px block.
    public static final double CORE_MIN = 6.0 / 16.0;
    public static final double CORE_MAX = 10.0 / 16.0;

    public static int pickArmDirIndex(double localX, double localY, double localZ, int fallbackDirIndex) {
        boolean inCoreY = localY >= CORE_MIN && localY <= CORE_MAX;
        boolean inCoreZ = localZ >= CORE_MIN && localZ <= CORE_MAX;
        boolean inCoreX = localX >= CORE_MIN && localX <= CORE_MAX;

        if (inCoreY && inCoreZ) {
            if (localX < CORE_MIN) return DirIndex.WEST;
            if (localX > CORE_MAX) return DirIndex.EAST;
        }
        if (inCoreX && inCoreY) {
            if (localZ < CORE_MIN) return DirIndex.NORTH;
            if (localZ > CORE_MAX) return DirIndex.SOUTH;
        }
        if (inCoreX && inCoreZ) {
            if (localY < CORE_MIN) return DirIndex.DOWN;
            if (localY > CORE_MAX) return DirIndex.UP;
        }

        return fallbackDirIndex;
    }
}
