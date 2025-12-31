package dev.cloudframe.fabric.platform;

import dev.cloudframe.common.platform.EntityRenderer;
import java.util.List;

/**
 * Fabric implementation of EntityRenderer (SKELETON ONLY).
 * 
 * TODO: Implement using Minecraft's entity system:
 * - net.minecraft.entity.decoration.DisplayEntity for visuals
 * - net.minecraft.entity.InteractionEntity for collision (if available)
 * - ServerWorld.spawnEntity() for spawning
 */
public class FabricEntityRenderer implements EntityRenderer {

    @Override
    public void spawnDisplayEntity(Object location, Object itemStack) {
        // TODO: Implement using DisplayEntity.ItemDisplayEntity
        throw new UnsupportedOperationException("Fabric EntityRenderer not yet implemented");
    }

    @Override
    public void spawnInteractionEntity(Object location, double width, double height) {
        // TODO: Implement using InteractionEntity (if available in Fabric)
        throw new UnsupportedOperationException("Fabric EntityRenderer not yet implemented");
    }

    @Override
    public void removeEntitiesAt(Object location) {
        // TODO: Implement by iterating nearby entities and removing tracked ones
        throw new UnsupportedOperationException("Fabric EntityRenderer not yet implemented");
    }

    @Override
    public List<Object> getActiveEntities() {
        // TODO: Return list of all tracked entity UUIDs
        throw new UnsupportedOperationException("Fabric EntityRenderer not yet implemented");
    }

    @Override
    public void shutdown() {
        // TODO: Clean up all tracked entities
        throw new UnsupportedOperationException("Fabric EntityRenderer not yet implemented");
    }
}
