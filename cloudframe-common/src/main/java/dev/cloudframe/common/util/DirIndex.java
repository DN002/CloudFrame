package dev.cloudframe.common.util;

/**
 * Shared 6-direction index convention used across CloudFrame.
 *
 * <p>Index mapping:</p>
 * <ul>
 *   <li>0 = EAST (+X)</li>
 *   <li>1 = WEST (-X)</li>
 *   <li>2 = UP (+Y)</li>
 *   <li>3 = DOWN (-Y)</li>
 *   <li>4 = SOUTH (+Z)</li>
 *   <li>5 = NORTH (-Z)</li>
 * </ul>
 */
public final class DirIndex {

    private DirIndex() {
    }

    public static final int EAST = 0;
    public static final int WEST = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    public static final int SOUTH = 4;
    public static final int NORTH = 5;

    public static boolean isValid(int dirIndex) {
        return dirIndex >= 0 && dirIndex < 6;
    }

    public static int opposite(int dirIndex) {
        return switch (dirIndex) {
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
            default -> -1;
        };
    }

    /**
     * Returns the direction index for a single-step delta.
     * If the delta is not a single axis step (e.g., diagonal or >1 block), returns -1.
     */
    public static int fromDelta(int dx, int dy, int dz) {
        if (dx == 1 && dy == 0 && dz == 0) return EAST;
        if (dx == -1 && dy == 0 && dz == 0) return WEST;
        if (dx == 0 && dy == 1 && dz == 0) return UP;
        if (dx == 0 && dy == -1 && dz == 0) return DOWN;
        if (dx == 0 && dy == 0 && dz == 1) return SOUTH;
        if (dx == 0 && dy == 0 && dz == -1) return NORTH;
        return -1;
    }

    public static int dx(int dirIndex) {
        return switch (dirIndex) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public static int dy(int dirIndex) {
        return switch (dirIndex) {
            case UP -> 1;
            case DOWN -> -1;
            default -> 0;
        };
    }

    public static int dz(int dirIndex) {
        return switch (dirIndex) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }

    public static String name(int dirIndex) {
        return switch (dirIndex) {
            case EAST -> "EAST";
            case WEST -> "WEST";
            case UP -> "UP";
            case DOWN -> "DOWN";
            case SOUTH -> "SOUTH";
            case NORTH -> "NORTH";
            default -> "UNKNOWN";
        };
    }
}
