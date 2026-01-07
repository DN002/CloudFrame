package dev.cloudframe.common.markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Shared validation + canonicalization for marker frames.
 *
 * <p>Fabric and Bukkit both use 4 marker blocks to define an axis-aligned rectangle.
 * This utility ensures the provided positions are exactly the four rectangle corners,
 * all on the same Y level, and returns them in a canonical order.</p>
 */
public final class MarkerFrameCanonicalizer {

    private MarkerFrameCanonicalizer() {
    }

    public enum Status {
        OK,
        WRONG_COUNT,
        NOT_SAME_Y,
        NOT_RECTANGLE_CORNERS,
        TOO_SMALL
    }

    public static final class Result {
        private final Status status;
        private final List<MarkerPos> corners;

        private Result(Status status, List<MarkerPos> corners) {
            this.status = status;
            this.corners = corners;
        }

        public Status status() {
            return status;
        }

        public boolean ok() {
            return status == Status.OK;
        }

        /** Canonical corners: (minX,minZ) -> (maxX,minZ) -> (maxX,maxZ) -> (minX,maxZ). */
        public List<MarkerPos> corners() {
            return corners;
        }
    }

    /**
     * Canonicalize marker corners.
     *
     * @param corners Exactly 4 positions
     * @param requireInsideArea If true, requires at least a 3x3 outer frame (i.e. inside area exists).
     */
    public static Result canonicalize(List<MarkerPos> corners, boolean requireInsideArea) {
        if (corners == null || corners.size() != 4) {
            return new Result(Status.WRONG_COUNT, List.of());
        }

        int y = corners.get(0).y();
        int minX = corners.get(0).x();
        int maxX = corners.get(0).x();
        int minZ = corners.get(0).z();
        int maxZ = corners.get(0).z();

        HashSet<MarkerPos> unique = new HashSet<>();
        for (MarkerPos c : corners) {
            if (c == null) continue;
            if (c.y() != y) {
                return new Result(Status.NOT_SAME_Y, List.of());
            }
            unique.add(c);
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }

        if (unique.size() != 4) {
            return new Result(Status.NOT_RECTANGLE_CORNERS, List.of());
        }

        if (requireInsideArea) {
            // Inside area exists iff there is at least one block strictly inside the perimeter.
            // That requires width >= 3 and length >= 3 => (max-min) >= 2 on each axis.
            if ((maxX - minX) < 2 || (maxZ - minZ) < 2) {
                return new Result(Status.TOO_SMALL, List.of());
            }
        }

        List<MarkerPos> canonical = MarkerSelectionState.cornersFromBounds(minX, y, minZ, maxX, maxZ);

        for (MarkerPos c : canonical) {
            if (!unique.contains(c)) {
                return new Result(Status.NOT_RECTANGLE_CORNERS, List.of());
            }
        }

        return new Result(Status.OK, new ArrayList<>(canonical));
    }
}
