package dev.cloudframe.fabric.tubes;

import java.util.UUID;

import dev.cloudframe.common.tubes.TubeNetworkManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class FabricTubeLocationAdapter implements TubeNetworkManager.ILocationAdapter {

    private final MinecraftServer server;

    public FabricTubeLocationAdapter(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof BlockPos pos) {
            return pos.add(dx, dy, dz);
        }
        return loc;
    }

    @Override
    public TubeNetworkManager.ChunkKey chunkKey(Object loc) {
        if (loc instanceof ChunkPos chunk) {
            return new TubeNetworkManager.ChunkKey(new UUID(0L, 0L), chunk.x, chunk.z);
        }
        if (loc instanceof BlockPos pos) {
            return new TubeNetworkManager.ChunkKey(new UUID(0L, 0L), pos.getX() >> 4, pos.getZ() >> 4);
        }
        return new TubeNetworkManager.ChunkKey(new UUID(0L, 0L), 0, 0);
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        if (loc instanceof BlockPos pos) {
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return false;
            return overworld.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        }
        return false;
    }

    @Override
    public boolean isInventoryAt(Object loc) {
        if (!(loc instanceof BlockPos pos)) return false;
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return false;
        BlockEntity blockEntity = overworld.getBlockEntity(pos);
        return blockEntity instanceof Inventory;
    }

    @Override
    public String worldName(Object loc) {
        return "minecraft:overworld";
    }

    @Override
    public UUID worldId(Object loc) {
        return new UUID(0L, 0L);
    }

    @Override
    public int blockX(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        if (loc instanceof BlockPos pos) return pos.getZ();
        return 0;
    }

    @Override
    public Object worldByName(String name) {
        return server.getOverworld();
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
