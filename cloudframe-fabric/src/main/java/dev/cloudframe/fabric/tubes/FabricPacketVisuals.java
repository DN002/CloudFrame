package dev.cloudframe.fabric.tubes;

import dev.cloudframe.common.tubes.ItemPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Fabric implementation of packet visuals using ItemDisplay entities.
 */
public class FabricPacketVisuals implements ItemPacket.IPacketVisuals {

    private final ServerWorld world;

    public FabricPacketVisuals(ServerWorld world) {
        this.world = world;
    }

    @Override
    public Object spawnEntity(Object startLocation, Object item) {
        if (!(startLocation instanceof BlockPos pos) || !(item instanceof ItemStack stack)) return null;
        
        Vec3d center = Vec3d.ofCenter(pos);
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPos(center.x, center.y, center.z);
        
        // Workaround: spawn entity then update via reflection or skip visual (tubes will still work)
        // For now, spawn without item display (entity-only collision, no visual)
        world.spawnEntity(display);
        return display;
    }

    @Override
    public void teleportEntity(Object entity, Object location) {
        if (!(entity instanceof Entity e) || !(location instanceof BlockPos pos)) return;
        Vec3d center = Vec3d.ofCenter(pos);
        e.setPos(center.x, center.y, center.z);
    }

    @Override
    public boolean isEntityDead(Object entity) {
        if (!(entity instanceof Entity e)) return true;
        return e.isRemoved() || !e.isAlive();
    }

    @Override
    public void removeEntity(Object entity) {
        if (entity instanceof Entity e) {
            e.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    @Override
    public boolean isChunkLoaded(Object location) {
        if (!(location instanceof BlockPos pos)) return false;
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Object interpolate(Object a, Object b, double progress01) {
        if (!(a instanceof BlockPos pa) || !(b instanceof BlockPos pb)) return a;
        double t = Math.max(0.0, Math.min(1.0, progress01));
        int x = (int) (pa.getX() + (pb.getX() - pa.getX()) * t);
        int y = (int) (pa.getY() + (pb.getY() - pa.getY()) * t);
        int z = (int) (pa.getZ() + (pb.getZ() - pa.getZ()) * t);
        return new BlockPos(x, y, z);
    }
}
