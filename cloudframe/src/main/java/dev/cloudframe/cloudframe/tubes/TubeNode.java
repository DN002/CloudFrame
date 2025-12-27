package dev.cloudframe.cloudframe.tubes;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class TubeNode {

    private final Location location;
    private final List<TubeNode> neighbors = new ArrayList<>();

    public TubeNode(Location location) {
        this.location = location;
    }

    public Location getLocation() {
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
