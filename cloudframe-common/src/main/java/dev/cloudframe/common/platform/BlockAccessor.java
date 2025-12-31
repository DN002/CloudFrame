package dev.cloudframe.common.platform;

import java.util.List;

/**
 * Platform-agnostic interface for block and inventory access.
 */
public interface BlockAccessor {
    
    /**
     * Get the block type at a location (as a string: "AIR", "CHEST", etc).
     */
    String getBlockType(Object location);
    
    /**
     * Set a block at a location.
     */
    void setBlock(Object location, String blockType);
    
    /**
     * Check if a location has an inventory (chest, hopper, etc).
     */
    boolean hasInventory(Object location);
    
    /**
     * Get the inventory at a location.
     */
    InventoryAccessor getInventory(Object location);
    
    /**
     * Get all nearby entities of a type (e.g., Player, ItemEntity).
     */
    List<Object> getNearbyEntities(Object location, double radius, String entityType);
    
    /**
     * Get the chunk at a location (for loaded/unloaded checks).
     */
    Object getChunk(Object location);
    
    /**
     * Check if a chunk is loaded.
     */
    boolean isChunkLoaded(Object chunk);
    
    /**
     * Drop an item naturally at a location.
     */
    void dropItemNaturally(Object location, Object itemStack);
}
