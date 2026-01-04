package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import net.minecraft.util.math.GlobalPos;
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
 * Lightweight Fabric pipe visuals using Interaction entities.
 */
public class FabricSimplePipeVisuals implements PipeNetworkManager.IPipeVisuals {

    private final Map<BlockPos, UUID> pipeEntities = new ConcurrentHashMap<>();
    private final ServerWorld world;

    public FabricSimplePipeVisuals(ServerWorld world) {
        this.world = world;
    }

    @Override
    public void updatePipeAndNeighbors(Object loc) {
        BlockPos pos;
        if (loc instanceof GlobalPos gp) {
            pos = gp.pos();
        } else if (loc instanceof BlockPos p) {
            pos = p;
        } else {
            return;
        }

        UUID oldId = pipeEntities.get(pos);
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

        pipeEntities.put(pos, interaction.getUuid());
    }

    @Override
    public void shutdown() {
        for (UUID entityId : pipeEntities.values()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        pipeEntities.clear();
    }
}
