package dev.cloudframe.fabric.pipes;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class FabricPipeLocationAdapter implements PipeNetworkManager.ILocationAdapter {

    private static final Debug debug = DebugManager.get(FabricPipeLocationAdapter.class);
    private static final AtomicBoolean warnedBlockPos = new AtomicBoolean(false);

    private final MinecraftServer server;

    public FabricPipeLocationAdapter(MinecraftServer server) {
        this.server = server;
    }

    private static void warnBlockPosOnce(String methodName) {
        if (warnedBlockPos.compareAndSet(false, true)) {
            debug.log(methodName,
                "WARNING: Fabric pipes received a dimension-less BlockPos; assuming overworld. " +
                "This can cause cross-dimension misrouting. Prefer GlobalPos everywhere.");
        }
    }

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().toImmutable());
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("normalize");
            // Back-compat: treat as overworld if dimension missing.
            return GlobalPos.create(World.OVERWORLD, pos.toImmutable());
        }
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof GlobalPos gp) {
            return GlobalPos.create(gp.dimension(), gp.pos().add(dx, dy, dz));
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("offset");
            return GlobalPos.create(World.OVERWORLD, pos.add(dx, dy, dz));
        }
        return loc;
    }

    @Override
    public PipeNetworkManager.ChunkKey chunkKey(Object loc) {
        if (loc instanceof ChunkPos chunk) {
            // ChunkPos is dimension-less; caller should prefer passing a GlobalPos or other dimension-aware key.
            return new PipeNetworkManager.ChunkKey(new UUID(0L, 0L), chunk.x, chunk.z);
        }
        if (loc instanceof GlobalPos gp) {
            UUID wid = worldId(gp);
            BlockPos pos = gp.pos();
            return new PipeNetworkManager.ChunkKey(wid, pos.getX() >> 4, pos.getZ() >> 4);
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("chunkKey");
            return new PipeNetworkManager.ChunkKey(worldId(World.OVERWORLD), pos.getX() >> 4, pos.getZ() >> 4);
        }
        return new PipeNetworkManager.ChunkKey(new UUID(0L, 0L), 0, 0);
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        if (loc instanceof GlobalPos gp) {
            ServerWorld world = server.getWorld(gp.dimension());
            if (world == null) return false;
            BlockPos pos = gp.pos();
            return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        }
        if (loc instanceof BlockPos pos) {
            warnBlockPosOnce("isChunkLoaded");
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return false;
            return overworld.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        }
        return false;
    }

    @Override
    public boolean isInventoryAt(Object loc) {
        if (loc instanceof GlobalPos gp) {
            ServerWorld world = server.getWorld(gp.dimension());
            if (world == null) return false;
            BlockEntity blockEntity = world.getBlockEntity(gp.pos());
            return blockEntity instanceof Inventory;
        }
        if (!(loc instanceof BlockPos pos)) return false;
        warnBlockPosOnce("isInventoryAt");
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return false;
        BlockEntity blockEntity = overworld.getBlockEntity(pos);
        return blockEntity instanceof Inventory;
    }

    @Override
    public String worldName(Object loc) {
        if (loc instanceof GlobalPos gp) {
            return gp.dimension().getValue().toString();
        }
        if (loc instanceof BlockPos) {
            warnBlockPosOnce("worldName");
        }
        return World.OVERWORLD.getValue().toString();
    }

    @Override
    public UUID worldId(Object loc) {
        if (loc instanceof GlobalPos gp) {
            return worldId(gp);
        }
        if (loc instanceof BlockPos) {
            warnBlockPosOnce("worldId");
        }
        return worldId(World.OVERWORLD);
    }

    private static UUID worldId(GlobalPos gp) {
        if (gp == null) return worldId(World.OVERWORLD);
        return UUID.nameUUIDFromBytes(gp.dimension().getValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static UUID worldId(RegistryKey<World> key) {
        if (key == null) key = World.OVERWORLD;
        return UUID.nameUUIDFromBytes(key.getValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public int blockX(Object loc) {
        if (loc instanceof GlobalPos gp) return gp.pos().getX();
        if (loc instanceof BlockPos pos) return pos.getX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        if (loc instanceof GlobalPos gp) return gp.pos().getY();
        if (loc instanceof BlockPos pos) return pos.getY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        if (loc instanceof GlobalPos gp) return gp.pos().getZ();
        if (loc instanceof BlockPos pos) return pos.getZ();
        return 0;
    }

    @Override
    public Object worldByName(String name) {
        if (name == null || name.isBlank()) return World.OVERWORLD;
        try {
            Identifier id = Identifier.of(name);
            return RegistryKey.of(RegistryKeys.WORLD, id);
        } catch (Throwable ignored) {
            return World.OVERWORLD;
        }
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        RegistryKey<World> key = World.OVERWORLD;
        if (world instanceof RegistryKey<?> rk) {
            @SuppressWarnings("unchecked")
            RegistryKey<World> wk = (RegistryKey<World>) rk;
            key = wk;
        } else if (world instanceof ServerWorld sw) {
            key = sw.getRegistryKey();
        } else if (world instanceof String s && !s.isBlank()) {
            try {
                key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(s));
            } catch (Throwable ignored) {
                key = World.OVERWORLD;
            }
        }

        return GlobalPos.create(key, new BlockPos(x, y, z));
    }
}
