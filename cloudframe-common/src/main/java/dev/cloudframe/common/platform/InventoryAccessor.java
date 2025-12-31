package dev.cloudframe.common.platform;

/**
 * Platform-agnostic interface for inventory operations.
 */
public interface InventoryAccessor {
    
    /**
     * Try to add an item to this inventory.
     * Returns remaining items if the inventory is full.
     */
    Object addItem(Object itemStack);
    
    /**
     * Get the remaining space for a given item type.
     */
    int getSpaceFor(Object itemStack);
    
    /**
     * Get all items in this inventory.
     */
    Object[] getContents();
    
    /**
     * Get the storage contents (excludes armor, offhand, etc for players).
     */
    Object[] getStorageContents();
    
    /**
     * Get the size of this inventory.
     */
    int getSize();
    
    /**
     * Check if this inventory is full.
     */
    boolean isFull();
}
