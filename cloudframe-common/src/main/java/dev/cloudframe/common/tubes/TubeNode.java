package dev.cloudframe.common.tubes;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-agnostic tube network node.
 * 
 * Stores location as Object to remain platform-neutral.
 * Bukkit: Object is org.bukkit.Location
 * Fabric: Object is net.minecraft.util.math.BlockPos + World reference
 */
public class TubeNode {

    private final Object location;
    private final List<TubeNode> neighbors = new ArrayList<>();

    public TubeNode(Object location) {
        this.location = location;
    }

    public Object getLocation() {
        return location;
    }

    public List<TubeNode> getNeighbors() {
        return neighbors;
    }

    public void addNeighbor(TubeNode node) {
        neighbors.add(node);
    }

    public void clearNeighbors() {
        neighbors.clear();
    }
}
