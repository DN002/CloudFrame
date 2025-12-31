package dev.cloudframe.fabric.tubes;

import dev.cloudframe.common.tubes.TubeNetworkManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight Fabric tube visuals using Interaction entities.
 */
public class FabricSimpleTubeVisuals implements TubeNetworkManager.ITubeVisuals {

    private final Map<BlockPos, UUID> tubeEntities = new ConcurrentHashMap<>();
    private final ServerWorld world;

    public FabricSimpleTubeVisuals(ServerWorld world) {
        this.world = world;
    }

    @Override
    public void updateTubeAndNeighbors(Object loc) {
        if (!(loc instanceof BlockPos pos)) return;
        
        UUID oldId = tubeEntities.get(pos);
        if (oldId != null) {
            Entity entity = world.getEntity(oldId);
            if (entity != null) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        Vec3d center = Vec3d.ofCenter(pos);
        InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
        interaction.setPos(center.x, center.y, center.z);
        world.spawnEntity(interaction);
        
        tubeEntities.put(pos, interaction.getUuid());
    }

    @Override
    public void shutdown() {
        for (UUID entityId : tubeEntities.values()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        tubeEntities.clear();
    }
}
