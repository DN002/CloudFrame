package dev.cloudframe.bukkit.tubes;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.common.tubes.ItemPacketManager.IItemDeliveryProvider;

/**
 * Bukkit implementation for delivering item packets into inventories.
 */
public class BukkitItemDeliveryProvider implements IItemDeliveryProvider {

    private static final int[][] DIRS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    @Override
    public boolean isChunkLoaded(Object location) {
        if (!(location instanceof Location loc)) return false;
        Chunk chunk = loc.getChunk();
        return chunk != null && chunk.isLoaded();
    }

    @Override
    public Object getInventoryHolder(Object blockLocation) {
        if (!(blockLocation instanceof Location loc)) return null;
        World world = loc.getWorld();
        if (world == null) return null;
        Block block = world.getBlockAt(loc);
        if (block.getState() instanceof InventoryHolder holder) {
            return holder;
        }
        return null;
    }

    @Override
    public int addItem(Object inventoryHolder, Object item) {
        if (!(inventoryHolder instanceof InventoryHolder holder)) return 0;
        if (!(item instanceof ItemStack stack)) return 0;
        Inventory inv = holder.getInventory();
        if (inv == null) return 0;
        int original = stack.getAmount();
        var leftovers = inv.addItem(stack.clone());
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return Math.max(0, original - remaining);
    }

    @Override
    public void dropItems(Object location, Object[] items) {
        if (!(location instanceof Location loc)) return;
        World world = loc.getWorld();
        if (world == null) return;
        for (Object obj : items) {
            if (obj instanceof ItemStack stack) {
                world.dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), stack);
            }
        }
    }

    @Override
    public Object getAdjacentBlockLocation(Object baseLocation, int dirIndex) {
        if (!(baseLocation instanceof Location base)) return null;
        if (dirIndex < 0 || dirIndex >= DIRS.length) return null;
        int[] d = DIRS[dirIndex];
        return new Location(base.getWorld(), base.getBlockX() + d[0], base.getBlockY() + d[1], base.getBlockZ() + d[2]);
    }
}
