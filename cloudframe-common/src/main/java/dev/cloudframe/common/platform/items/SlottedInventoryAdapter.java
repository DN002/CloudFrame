package dev.cloudframe.common.platform.items;

/**
 * Platform adapter for inventory slot operations.
 *
 * @param <INV> platform inventory type (e.g., Bukkit Inventory, Fabric Inventory)
 * @param <STACK> platform stack type
 */
public interface SlottedInventoryAdapter<INV, STACK> {

    int size(INV inventory);

    STACK getStack(INV inventory, int slot);

    void setStack(INV inventory, int slot, STACK stack);

    void markDirty(INV inventory);
}
