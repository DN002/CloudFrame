package dev.cloudframe.common.pipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;

/**
 * Platform-agnostic item packet representing an item moving through pipes.
 * Handles movement progression, waypoint traversal, and delivery callbacks.
 */
public class ItemPacket {

    private static final Debug debug = DebugManager.get(ItemPacket.class);

    private final Object item; // ItemStack (platform-specific)
    private final IItemStackAdapter itemAdapter;
    private final List<Object> waypoints; // Location objects (platform-specific)
    private final Object destinationInventory; // nullable Location (platform-specific)
    private final BiConsumer<Object, Integer> onDeliveryCallback; // nullable callback

    private int currentIndex = 0;
    private double progress = 0.0;

    // Visual speed per tick along a segment. Lower = smoother (more steps per block).
    private static final double SPEED = 0.125;

    private Object entity; // visual packet entity (platform-specific)
    private IPacketVisuals visuals; // platform provider for visuals

    public interface IPacketVisuals {
        Object spawnEntity(Object startLocation, Object item);
        void teleportEntity(Object entity, Object location);
        boolean isEntityDead(Object entity);
        void removeEntity(Object entity);
        boolean isChunkLoaded(Object location);
        Object interpolate(Object a, Object b, double progress01);
    }

    public interface IItemStackAdapter {
        int getAmount(Object item);
        Object withAmount(Object item, int amount);
    }

    public ItemPacket(Object item, List<PipeNode> path, IPacketVisuals visuals, IItemStackAdapter itemAdapter) {
        this(item, toWaypoints(path), null, null, visuals, itemAdapter);
    }

    public ItemPacket(Object item, List<Object> waypoints, Object destinationInventory, IPacketVisuals visuals, IItemStackAdapter itemAdapter) {
        this(item, waypoints, destinationInventory, null, visuals, itemAdapter);
    }

    public ItemPacket(Object item, List<Object> waypoints, Object destinationInventory,
                      BiConsumer<Object, Integer> onDeliveryCallback, IPacketVisuals visuals,
                      IItemStackAdapter itemAdapter) {
        this.item = Objects.requireNonNull(item, "item");
        this.itemAdapter = Objects.requireNonNull(itemAdapter, "itemAdapter");
        this.waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
        this.destinationInventory = destinationInventory;
        this.onDeliveryCallback = onDeliveryCallback;
        this.visuals = Objects.requireNonNull(visuals, "visuals");

        if (this.waypoints.size() < 2) {
            throw new IllegalArgumentException("ItemPacket requires at least 2 waypoints");
        }

        debug.log("constructor", "Created packet pathLength=" + this.waypoints.size());
        spawnEntity();
    }

    private static List<Object> toWaypoints(List<PipeNode> path) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Pipe path must not be empty");
        }

        List<Object> points = new ArrayList<>(path.size());
        for (PipeNode node : path) {
            Object loc = node.getLocation();
            if (loc == null) continue;
            points.add(loc);
        }

        if (points.size() < 2) {
            Object only = points.isEmpty() ? path.get(0).getLocation() : points.get(0);
            points.add(only);
        }
        return points;
    }

    private void spawnEntity() {
        Object start = waypoints.get(0);
        debug.log("spawnEntity", "Spawning packet entity");
        this.entity = visuals.spawnEntity(start, item);
    }

    public boolean tick(boolean shouldLog) {
        if (visuals.isEntityDead(entity)) {
            if (shouldLog) {
                debug.log("tick", "Entity missing or dead — finishing packet");
            }
            return true;
        }

        Object loc = visuals.isChunkLoaded(waypoints.get(currentIndex)) ? waypoints.get(currentIndex) : null;
        if (loc == null || !visuals.isChunkLoaded(loc)) {
            if (shouldLog) {
                debug.log("tick", "Chunk unloaded — pausing packet");
            }
            return false;
        }

        if (currentIndex < waypoints.size() - 1) {
            Object nextLoc = waypoints.get(currentIndex + 1);
            if (!visuals.isChunkLoaded(nextLoc)) {
                if (shouldLog) {
                    debug.log("tick", "Next chunk unloaded — pausing packet");
                }
                return false;
            }
        }

        if (currentIndex >= waypoints.size() - 1) {
            if (shouldLog) {
                debug.log("tick", "Reached final node — finishing packet");
            }
            return true;
        }

        progress += SPEED;

        if (progress >= 1.0) {
            progress = 0.0;
            currentIndex++;
            if (shouldLog) {
                debug.log("tick", "Advancing to next pipe node index=" + currentIndex);
            }
        }

        moveEntity(shouldLog);
        return false;
    }

    private void moveEntity(boolean shouldLog) {
        Object a = waypoints.get(currentIndex);
        Object b = waypoints.get(Math.min(currentIndex + 1, waypoints.size() - 1));

        Object newLoc = visuals.interpolate(a, b, progress);
        visuals.teleportEntity(entity, newLoc);

        if (shouldLog) {
            debug.log("moveEntity", "Progress=" + String.format("%.2f", progress) +
                    " index=" + currentIndex);
        }
    }

    public void destroy() {
        debug.log("destroy", "Destroying packet");
        visuals.removeEntity(entity);
    }

    public Object getItem() {
        return item;
    }

    public int getItemAmount() {
        return itemAdapter.getAmount(item);
    }

    public Object getDestinationInventory() {
        return destinationInventory;
    }

    public Object getLastWaypoint() {
        return waypoints.get(waypoints.size() - 1);
    }

    public int getPathLength() {
        return waypoints.size();
    }

    public List<Object> getWaypoints() {
        return waypoints;
    }

    public double getProgress() {
        return progress;
    }

    public BiConsumer<Object, Integer> getOnDeliveryCallback() {
        return onDeliveryCallback;
    }

    public Object createLeftoverItem(int amount) {
        return itemAdapter.withAmount(item, amount);
    }
}
