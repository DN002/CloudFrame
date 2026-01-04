package dev.cloudframe.common.pipes;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-agnostic pipe network node.
 *
 * Stores location as Object to remain platform-neutral.
 * Bukkit: Object is org.bukkit.Location
 * Fabric: Object is net.minecraft.util.math.BlockPos + World reference
 */
public class PipeNode {

    private final Object location;
    private final List<PipeNode> neighbors = new ArrayList<>();

    // Bitmask for disabled inventory connections (one bit per direction: 6 bits for 0-5)
    private int disabledInventorySides = 0;

    public PipeNode(Object location) {
        this.location = location;
    }

    public Object getLocation() {
        return location;
    }

    public List<PipeNode> getNeighbors() {
        return neighbors;
    }

    public void addNeighbor(PipeNode node) {
        neighbors.add(node);
    }

    public void clearNeighbors() {
        neighbors.clear();
    }

    public boolean isInventorySideDisabled(int dirIndex) {
        if (dirIndex < 0 || dirIndex >= 6) return false;
        return (disabledInventorySides & (1 << dirIndex)) != 0;
    }

    public void setInventorySideDisabled(int dirIndex, boolean disabled) {
        if (dirIndex < 0 || dirIndex >= 6) return;
        if (disabled) {
            disabledInventorySides |= (1 << dirIndex);
        } else {
            disabledInventorySides &= ~(1 << dirIndex);
        }
    }

    public void toggleInventorySideDisabled(int dirIndex) {
        setInventorySideDisabled(dirIndex, !isInventorySideDisabled(dirIndex));
    }

    public int getDisabledInventorySides() {
        return disabledInventorySides;
    }

    public void setDisabledInventorySides(int mask) {
        this.disabledInventorySides = mask & 0x3F;
    }
}
