package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.platform.items.ItemStackAdapter;
import dev.cloudframe.common.platform.items.ItemStackKeyAdapter;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Fabric implementation for ItemStack operations.
 */
public class FabricItemStackAdapter implements ItemStackAdapter<ItemStack>, ItemStackKeyAdapter<ItemStack> {

    public static final FabricItemStackAdapter INSTANCE = new FabricItemStackAdapter();

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public int getCount(ItemStack stack) {
        return stack == null ? 0 : stack.getCount();
    }

    @Override
    public void setCount(ItemStack stack, int count) {
        if (stack == null) return;
        stack.setCount(count);
    }

    @Override
    public int getMaxCount(ItemStack stack) {
        return stack == null ? 0 : stack.getMaxCount();
    }

    @Override
    public ItemStack copy(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public boolean canMerge(ItemStack existing, ItemStack incoming) {
        if (existing == null || incoming == null) return false;
        return ItemStack.areItemsAndComponentsEqual(existing, incoming);
    }

    @Override
    public String key(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        try {
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            // Components include enchantments, custom names, etc (best-effort stable signature).
            // Count is not included.
            String comps;
            try {
                comps = String.valueOf(stack.getComponents());
            } catch (Throwable ignored) {
                comps = "";
            }
            return comps.isBlank() ? id : (id + "|" + comps);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
