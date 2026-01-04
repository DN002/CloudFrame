package dev.cloudframe.common.platform;

import java.util.List;

/**
 * Platform-agnostic interface for entity spawning and management.
 * Implementations handle creating visual entities (displays, collision) for different modloaders.
 */
public interface EntityRenderer {
    
    /**
     * Spawn a visible 3D display entity at the given location.
        * Used for pipe arms, controller displays, etc.
     */
    void spawnDisplayEntity(Object location, Object itemStack);
    
    /**
     * Spawn an invisible interaction entity for collision/clicking.
     */
    void spawnInteractionEntity(Object location, double width, double height);
    
    /**
     * Remove all entities for a given block location.
     */
    void removeEntitiesAt(Object location);
    
    /**
     * Get all active display entities (for cleanup on chunk unload).
     */
    List<Object> getActiveEntities();
    
    /**
     * Clean up all entities (called on shutdown).
     */
    void shutdown();
}
