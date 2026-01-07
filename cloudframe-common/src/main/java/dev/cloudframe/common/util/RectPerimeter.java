package dev.cloudframe.common.util;

/**
 * Deterministic perimeter iteration for an axis-aligned rectangle on the X/Z plane.
 *
 * <p>Ordering matches the historical Fabric implementation:
 * <ul>
 *   <li>Top edge: z=minZ, x=minX..maxX</li>
 *   <li>Bottom edge: z=maxZ, x=minX..maxX (skipped when minZ==maxZ)</li>
 *   <li>Left edge: x=minX, z=minZ+1..maxZ-1 (skipped when minX==maxX)</li>
 *   <li>Right edge: x=maxX, z=minZ+1..maxZ-1 (skipped when minX==maxX)</li>
 * </ul>
 * Corners are included only by the top/bottom edges (no duplicates from left/right).</p>
 */
public final class RectPerimeter {

    private RectPerimeter() {
    }

    public record Pos(int x, int z) {
    }

    public static int count(int minX, int minZ, int maxX, int maxZ) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        int width = aMaxX - aMinX;
        int depth = aMaxZ - aMinZ;
        if (width < 0 || depth < 0) return 0;

        if (width == 0 && depth == 0) return 1;
        if (depth == 0) return width + 1;
        if (width == 0) return depth + 1;

        // Rectangle ring: 2*(width+1) + 2*(depth-1)
        return 2 * (width + 1) + 2 * (depth - 1);
    }

    /**
     * Returns the perimeter position at {@code index}, or null if out of range.
     */
    public static Pos at(int minX, int minZ, int maxX, int maxZ, int index) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        int width = aMaxX - aMinX;
        int depth = aMaxZ - aMinZ;
        if (width < 0 || depth < 0) return null;

        int topLen = width + 1;
        int bottomLen = (aMinZ == aMaxZ) ? 0 : (width + 1);
        int sideLen = Math.max(0, depth - 1);
        int leftLen = (aMinX == aMaxX) ? 0 : sideLen;
        int rightLen = (aMinX == aMaxX) ? 0 : sideLen;

        int total = topLen + bottomLen + leftLen + rightLen;
        if (index < 0 || index >= total) return null;

        int i = index;

        // stage 0: top edge
        if (i < topLen) {
            return new Pos(aMinX + i, aMinZ);
        }
        i -= topLen;

        // stage 1: bottom edge
        if (i < bottomLen) {
            return new Pos(aMinX + i, aMaxZ);
        }
        i -= bottomLen;

        // stage 2: left edge (excluding corners)
        if (i < leftLen) {
            return new Pos(aMinX, (aMinZ + 1) + i);
        }
        i -= leftLen;

        // stage 3: right edge (excluding corners)
        if (i < rightLen) {
            return new Pos(aMaxX, (aMinZ + 1) + i);
        }

        return null;
    }
}
