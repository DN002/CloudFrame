package dev.cloudframe.bukkit.platform;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
import dev.cloudframe.common.platform.BlockAccessor;
import dev.cloudframe.common.platform.InventoryAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit implementation of BlockAccessor.
 */
public class BukkitBlockAccessor implements BlockAccessor {

    @Override
    public String getBlockType(Object locationObj) {
        if (!(locationObj instanceof Location location)) return "UNKNOWN";
        World world = location.getWorld();
        if (world == null) return "UNKNOWN";
        return world.getBlockAt(location).getType().toString();
    }

    @Override
    public void setBlock(Object locationObj, String blockType) {
        if (!(locationObj instanceof Location location)) return;
        World world = location.getWorld();
        if (world == null) return;
        try {
            Material mat = Material.valueOf(blockType);
            world.getBlockAt(location).setType(mat, false);
        } catch (IllegalArgumentException ex) {
            // Invalid block type
        }
    }

    @Override
    public boolean hasInventory(Object locationObj) {
        if (!(locationObj instanceof Location location)) return false;
        World world = location.getWorld();
        if (world == null) return false;
        Block block = world.getBlockAt(location);
        return block.getState() instanceof InventoryHolder;
    }

    @Override
    public InventoryAccessor getInventory(Object locationObj) {
        if (!(locationObj instanceof Location location)) return null;
        World world = location.getWorld();
        if (world == null) return null;
        Block block = world.getBlockAt(location);
        if (block.getState() instanceof InventoryHolder holder) {
            return new BukkitInventoryAccessor(holder.getInventory());
        }
        return null;
    }

    @Override
    public List<Object> getNearbyEntities(Object locationObj, double radius, String entityType) {
        if (!(locationObj instanceof Location location)) return new ArrayList<>();
        World world = location.getWorld();
        if (world == null) return new ArrayList<>();
        List<Object> result = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            result.add(entity);
        }
        return result;
    }

    @Override
    public Object getChunk(Object locationObj) {
        if (!(locationObj instanceof Location location)) return null;
        World world = location.getWorld();
        if (world == null) return null;
        return world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public boolean isChunkLoaded(Object chunkObj) {
        if (!(chunkObj instanceof Chunk chunk)) return false;
        return chunk.isLoaded();
    }

    @Override
    public void dropItemNaturally(Object locationObj, Object itemStackObj) {
        if (!(locationObj instanceof Location location)) return;
        if (!(itemStackObj instanceof org.bukkit.inventory.ItemStack itemStack)) return;
        World world = location.getWorld();
        if (world == null) return;
        try {
            world.dropItem(location.clone().add(0.5, 0.5, 0.5), itemStack);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
