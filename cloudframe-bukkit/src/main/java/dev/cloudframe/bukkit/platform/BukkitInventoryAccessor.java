package dev.cloudframe.bukkit.platform;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import dev.cloudframe.common.platform.InventoryAccessor;

/**
 * Bukkit implementation of InventoryAccessor.
 */
public class BukkitInventoryAccessor implements InventoryAccessor {
    private final Inventory inventory;

    public BukkitInventoryAccessor(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Object addItem(Object itemStackObj) {
        if (!(itemStackObj instanceof ItemStack itemStack)) return itemStackObj;
        var result = inventory.addItem(itemStack);
        return result.isEmpty() ? null : result.values().stream().findFirst().orElse(null);
    }

    @Override
    public int getSpaceFor(Object itemStackObj) {
        if (!(itemStackObj instanceof ItemStack itemStack)) return 0;
        int space = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getAmount() == 0) {
                space += itemStack.getMaxStackSize();
            } else if (slot.isSimilar(itemStack)) {
                space += itemStack.getMaxStackSize() - slot.getAmount();
            }
        }
        return space;
    }

    @Override
    public Object[] getContents() {
        ItemStack[] items = inventory.getContents();
        Object[] result = new Object[items.length];
        System.arraycopy(items, 0, result, 0, items.length);
        return result;
    }

    @Override
    public Object[] getStorageContents() {
        return getContents();
    }

    @Override
    public int getSize() {
        return inventory.getSize();
    }

    @Override
    public boolean isFull() {
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getAmount() == 0) return false;
        }
        return true;
    }
}
