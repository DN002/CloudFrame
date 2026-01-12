package dev.cloudframe.cloudframe.tubes;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ItemPacket {

    private static final Debug debug = DebugManager.get(ItemPacket.class);

    private final ItemStack item;
    private final List<Location> waypoints;
    private final Location destinationInventory; // nullable
    private final BiConsumer<Location, Integer> onDeliveryCallback; // nullable

    private int currentIndex = 0;
    private double progress = 0.0;

    private static final double SPEED = 0.2;

    private ItemDisplay entity; // visual packet entity

    public ItemPacket(ItemStack item, List<TubeNode> path) {
        this(item, toWaypoints(path), null, null);
    }

    public ItemPacket(ItemStack item, List<Location> waypoints, Location destinationInventory) {
        this(item, waypoints, destinationInventory, null);
    }

    public ItemPacket(ItemStack item, List<Location> waypoints, Location destinationInventory, BiConsumer<Location, Integer> onDeliveryCallback) {
        this.item = item;
        this.waypoints = List.copyOf(waypoints);
        this.destinationInventory = destinationInventory;
        this.onDeliveryCallback = onDeliveryCallback;

        if (this.waypoints.size() < 2) {
            throw new IllegalArgumentException("ItemPacket requires at least 2 waypoints");
        }

        debug.log("constructor", "Created packet for item=" + item.getType() +
                " pathLength=" + this.waypoints.size());

        spawnEntity();
    }

    private static List<Location> toWaypoints(List<TubeNode> path) {
        Objects.requireNonNull(path, "path");
        if (path.size() < 1) {
            throw new IllegalArgumentException("Tube path must not be empty");
        }

        List<Location> points = new ArrayList<>(path.size());
        for (TubeNode node : path) {
            Location loc = node.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            points.add(loc.clone().add(0.5, 0.5, 0.5));
        }
        if (points.size() < 2) {
            // If there's only one tube, duplicate to create a valid segment.
            Location only = points.isEmpty() ? path.get(0).getLocation().clone().add(0.5, 0.5, 0.5) : points.get(0);
            points.add(only.clone());
        }
        return points;
    }

    private void spawnEntity() {
        Location start = waypoints.get(0).clone();
        World world = start.getWorld();

        debug.log("spawnEntity", "Spawning packet entity at " + start);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(start, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());

        // Make it feel "in-tube": smaller + centered.
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new org.joml.Quaternionf(),
            new Vector3f(0.35f, 0.35f, 0.35f),
            new org.joml.Quaternionf()
        ));

        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);

        // Smooth movement between teleports.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(1);

        // Keep it rendered even if the player is close.
        display.setBillboard(Display.Billboard.FIXED);

        this.entity = display;
    }

    public boolean tick(boolean shouldLog) {
        if (entity == null || entity.isDead()) {
            if (shouldLog) {
                debug.log("tick", "Entity missing or dead — finishing packet");
            }
            return true;
        }

        Location loc = entity.getLocation();
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            if (shouldLog) {
                debug.log("tick", "Chunk unloaded — pausing packet");
            }
            return false;
        }

        // Check next chunk before moving
        if (currentIndex < waypoints.size() - 1) {
            Location nextLoc = waypoints.get(currentIndex + 1);
            if (nextLoc.getWorld() != null && !nextLoc.getWorld().isChunkLoaded(nextLoc.getBlockX() >> 4, nextLoc.getBlockZ() >> 4)) {
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
                debug.log("tick", "Advancing to next tube node index=" + currentIndex);
            }
        }

        moveEntity(shouldLog);
        return false;
    }

    private void moveEntity(boolean shouldLog) {
        Location a = waypoints.get(currentIndex);
        Location b = waypoints.get(Math.min(currentIndex + 1, waypoints.size() - 1));

        double x = a.getX() + (b.getX() - a.getX()) * progress;
        double y = a.getY() + (b.getY() - a.getY()) * progress;
        double z = a.getZ() + (b.getZ() - a.getZ()) * progress;

        Location newLoc = new Location(a.getWorld(), x, y, z);

        entity.teleport(newLoc);

        if (shouldLog) {
            debug.log("moveEntity", "Packet moved to " + newLoc +
                    " progress=" + progress +
                    " index=" + currentIndex);
        }
    }

    public void destroy() {
        debug.log("destroy", "Destroying packet for item=" + item.getType());

        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    public ItemStack getItem() {
        return item;
    }

    public Location getDestinationInventory() {
        return destinationInventory;
    }

    public Location getLastWaypoint() {
        return waypoints.get(waypoints.size() - 1);
    }

    public int getPathLength() {
        return waypoints.size();
    }

    public List<Location> getWaypoints() {
        return waypoints;
    }

    public double getProgress() {
        return progress;
    }

    public BiConsumer<Location, Integer> getOnDeliveryCallback() {
        return onDeliveryCallback;
    }
}
