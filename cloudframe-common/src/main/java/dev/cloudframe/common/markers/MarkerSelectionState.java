package dev.cloudframe.common.markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Platform-neutral marker selection state.
 *
 * A selection is a set of up to 4 corners on the same Y-plane and world.
 */
public final class MarkerSelectionState {

    private final List<MarkerPos> corners;
    private final int yLevel;
    private boolean activated;
    private final String worldId;

    public MarkerSelectionState(List<MarkerPos> corners, int yLevel, boolean activated, String worldId) {
        this.corners = corners == null ? new ArrayList<>() : new ArrayList<>(corners);
        this.yLevel = yLevel;
        this.activated = activated;
        this.worldId = worldId;
    }

    public List<MarkerPos> corners() {
        return Collections.unmodifiableList(corners);
    }

    public int yLevel() {
        return yLevel;
    }

    public boolean activated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String worldId() {
        return worldId;
    }

    public boolean isComplete() {
        return corners.size() == 4;
    }

    public boolean containsCorner(MarkerPos pos) {
        if (pos == null) return false;
        for (MarkerPos c : corners) {
            if (c != null && c.equals(pos)) return true;
        }
        return false;
    }

    public void addCorner(MarkerPos pos) {
        if (pos == null) return;
        corners.add(pos);
    }

    public int minX() {
        int v = corners.get(0).x();
        for (MarkerPos c : corners) v = Math.min(v, c.x());
        return v;
    }

    public int maxX() {
        int v = corners.get(0).x();
        for (MarkerPos c : corners) v = Math.max(v, c.x());
        return v;
    }

    public int minZ() {
        int v = corners.get(0).z();
        for (MarkerPos c : corners) v = Math.min(v, c.z());
        return v;
    }

    public int maxZ() {
        int v = corners.get(0).z();
        for (MarkerPos c : corners) v = Math.max(v, c.z());
        return v;
    }

    public static List<MarkerPos> cornersFromBounds(int minX, int y, int minZ, int maxX, int maxZ) {
        List<MarkerPos> corners = new ArrayList<>(4);
        corners.add(new MarkerPos(minX, y, minZ));
        corners.add(new MarkerPos(maxX, y, minZ));
        corners.add(new MarkerPos(maxX, y, maxZ));
        corners.add(new MarkerPos(minX, y, maxZ));
        return corners;
    }
}
