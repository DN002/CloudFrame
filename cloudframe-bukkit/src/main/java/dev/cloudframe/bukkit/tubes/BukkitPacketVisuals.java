package dev.cloudframe.bukkit.tubes;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import dev.cloudframe.common.tubes.ItemPacket.IPacketVisuals;

/**
 * Bukkit implementation of packet visuals using ItemDisplay.
 */
public class BukkitPacketVisuals implements IPacketVisuals {

    @Override
    public Object spawnEntity(Object startLocation, Object item) {
        if (!(startLocation instanceof Location loc) || !(item instanceof ItemStack stack)) return null;
        World world = loc.getWorld();
        if (world == null) return null;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        display.setPersistent(false);
        display.setGravity(false);
        // Shrink slightly so packets are less obtrusive
        display.setTransformation(new Transformation(new Vector3f(), display.getTransformation().getLeftRotation(), new Vector3f(0.45f, 0.45f, 0.45f), display.getTransformation().getRightRotation()));
        return display;
    }

    @Override
    public void teleportEntity(Object entity, Object location) {
        if (!(entity instanceof Entity e) || !(location instanceof Location loc)) return;
        e.teleport(loc.clone().add(0.5, 0.5, 0.5));
    }

    @Override
    public boolean isEntityDead(Object entity) {
        if (!(entity instanceof Entity e)) return true;
        return e.isDead() || !e.isValid();
    }

    @Override
    public void removeEntity(Object entity) {
        if (entity instanceof Entity e) {
            e.remove();
        }
    }

    @Override
    public boolean isChunkLoaded(Object location) {
        if (!(location instanceof Location loc)) return false;
        Chunk chunk = loc.getChunk();
        return chunk != null && chunk.isLoaded();
    }

    @Override
    public Object interpolate(Object a, Object b, double progress01) {
        if (!(a instanceof Location la) || !(b instanceof Location lb)) return a;
        double t = Math.max(0.0, Math.min(1.0, progress01));
        double x = la.getX() + (lb.getX() - la.getX()) * t;
        double y = la.getY() + (lb.getY() - la.getY()) * t;
        double z = la.getZ() + (lb.getZ() - la.getZ()) * t;
        return new Location(la.getWorld(), x, y, z);
    }
}
