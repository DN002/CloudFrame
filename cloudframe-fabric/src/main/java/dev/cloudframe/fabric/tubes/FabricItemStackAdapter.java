package dev.cloudframe.fabric.tubes;

import dev.cloudframe.common.tubes.ItemPacket;
import net.minecraft.item.ItemStack;

/**
 * Fabric implementation for ItemStack operations.
 */
public class FabricItemStackAdapter implements ItemPacket.IItemStackAdapter {

    @Override
    public int getAmount(Object itemStack) {
        if (itemStack instanceof ItemStack stack) {
            return stack.getCount();
        }
        throw new IllegalArgumentException("Expected ItemStack, got " + itemStack.getClass());
    }

    @Override
    public Object withAmount(Object itemStack, int amount) {
        if (itemStack instanceof ItemStack stack) {
            ItemStack newStack = stack.copy();
            newStack.setCount(amount);
            return newStack;
        }
        throw new IllegalArgumentException("Expected ItemStack, got " + itemStack.getClass());
    }
}
