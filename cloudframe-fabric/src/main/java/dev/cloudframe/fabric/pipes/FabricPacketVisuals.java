package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.pipes.ItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

/**
 * Fabric implementation of packet visuals using ItemDisplay entities.
 */
public class FabricPacketVisuals implements ItemPacket.IPacketVisuals {

    private final MinecraftServer server;

    public FabricPacketVisuals(MinecraftServer server) {
        this.server = server;
    }

    private ServerWorld worldOf(Object location) {
        if (location instanceof GlobalPos gp) {
            ServerWorld w = server.getWorld(gp.dimension());
            return w != null ? w : server.getOverworld();
        }
        return server.getOverworld();
    }

    private BlockPos posOf(Object location) {
        if (location instanceof GlobalPos gp) return gp.pos();
        if (location instanceof BlockPos pos) return pos;
        return null;
    }

    @Override
    public Object spawnEntity(Object startLocation, Object item) {
        BlockPos pos = posOf(startLocation);
        if (pos == null || !(item instanceof ItemStack stack)) return null;
        ServerWorld world = worldOf(startLocation);

        Vec3d center = Vec3d.ofCenter(pos);
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPos(center.x, center.y, center.z);
        display.setItemStack(stack.copyWithCount(1));

        // Make movement appear smooth on the client.
        // Display entities support client interpolation via teleport/interpolation durations (method names vary by mappings/version).
        try {
            var m = display.getClass().getMethod("setTeleportDuration", int.class);
            m.invoke(display, 2);
        } catch (Throwable ignored) {}
        try {
            var m = display.getClass().getMethod("setInterpolationDuration", int.class);
            m.invoke(display, 2);
        } catch (Throwable ignored) {}

        // Scale down the item display to fit inside pipes (0.2 = 20% of normal size)
        org.joml.Matrix4f matrix = new org.joml.Matrix4f();
        matrix.scale(0.2f, 0.2f, 0.2f);
        display.setTransformation(new net.minecraft.util.math.AffineTransformation(matrix));

        world.spawnEntity(display);
        return display;
    }

    @Override
    public void teleportEntity(Object entity, Object location) {
        if (!(entity instanceof Entity e)) return;

        if (location instanceof Vec3d v) {
            e.setPos(v.x, v.y, v.z);
            return;
        }

        BlockPos pos = posOf(location);
        if (pos != null) {
            Vec3d center = Vec3d.ofCenter(pos);
            e.setPos(center.x, center.y, center.z);
        }
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
        BlockPos pos = posOf(location);
        if (pos == null) return false;
        ServerWorld world = worldOf(location);
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Object interpolate(Object a, Object b, double progress01) {
        BlockPos pa = posOf(a);
        BlockPos pb = posOf(b);
        if (pa == null || pb == null) return a;
        double t = Math.max(0.0, Math.min(1.0, progress01));

        Vec3d start = Vec3d.ofCenter(pa);
        Vec3d end = Vec3d.ofCenter(pb);
        return new Vec3d(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        );
    }
}
