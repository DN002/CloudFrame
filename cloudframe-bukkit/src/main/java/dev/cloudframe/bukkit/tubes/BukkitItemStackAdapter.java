package dev.cloudframe.bukkit.tubes;

import org.bukkit.inventory.ItemStack;

import dev.cloudframe.common.tubes.ItemPacket.IItemStackAdapter;

/**
 * Bukkit implementation of item stack adapter for ItemPacket.
 */
public class BukkitItemStackAdapter implements IItemStackAdapter {
    @Override
    public int getAmount(Object item) {
        if (item instanceof ItemStack stack) {
            return stack.getAmount();
        }
        return 0;
    }

    @Override
    public Object withAmount(Object item, int amount) {
        if (item instanceof ItemStack stack) {
            ItemStack copy = stack.clone();
            copy.setAmount(Math.max(0, amount));
            return copy;
        }
        return item;
    }
}
