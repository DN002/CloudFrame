package dev.cloudframe.cloudframe.tubes;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ItemPacket {

    private static final Debug debug = DebugManager.get(ItemPacket.class);

    private final ItemStack item;
    private final List<TubeNode> path;

    private int currentIndex = 0;
    private double progress = 0.0;

    private static final double SPEED = 0.2;

    private ArmorStand entity; // floating item entity

    public ItemPacket(ItemStack item, List<TubeNode> path) {
        this.item = item;
        this.path = path;

        debug.log("constructor", "Created packet for item=" + item.getType() +
                " pathLength=" + path.size());

        spawnEntity();
    }

    private void spawnEntity() {
        Location start = path.get(0).getLocation().clone().add(0.5, 0.1, 0.5);
        World world = start.getWorld();

        debug.log("spawnEntity", "Spawning packet entity at " + start);

        ArmorStand stand = world.spawn(start, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);        // no hitbox
            s.setGravity(false);
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setPersistent(false);
        });

        this.entity = stand;
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
        if (currentIndex < path.size() - 1) {
            TubeNode next = path.get(currentIndex + 1);
            Location nextLoc = next.getLocation();
            if (!nextLoc.getWorld().isChunkLoaded(nextLoc.getBlockX() >> 4, nextLoc.getBlockZ() >> 4)) {
                if (shouldLog) {
                    debug.log("tick", "Next chunk unloaded — pausing packet");
                }
                return false;
            }
        }

        if (currentIndex >= path.size() - 1) {
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
        TubeNode from = path.get(currentIndex);
        TubeNode to = path.get(Math.min(currentIndex + 1, path.size() - 1));

        Location a = from.getLocation().clone().add(0.5, 0.1, 0.5);
        Location b = to.getLocation().clone().add(0.5, 0.1, 0.5);

        double x = a.getX() + (b.getX() - a.getX()) * progress;
        double y = a.getY() + (b.getY() - a.getY()) * progress;
        double z = a.getZ() + (b.getZ() - a.getZ()) * progress;

        Location newLoc = new Location(a.getWorld(), x, y, z);

        entity.teleport(newLoc);

        newLoc.getWorld().spawnParticle(
            Particle.SOUL_FIRE_FLAME,
            newLoc,
            1,
            0.02, 0.02, 0.02,
            0
        );

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

    public TubeNode getDestination() {
        return path.get(path.size() - 1);
    }

    public List<TubeNode> getPath() {
        return path;
    }

    public double getProgress() {
        return progress;
    }
}
