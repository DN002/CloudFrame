package dev.cloudframe.bukkit.pipes;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.platform.world.WorldKeyAdapter;

/**
 * Bukkit implementation of {@link PipeNetworkManager.ILocationAdapter}.
 */
public class BukkitPipeLocationAdapter implements PipeNetworkManager.ILocationAdapter {

    private static final WorldKeyAdapter<Object> WORLD_KEYS = new WorldKeyAdapter<>() {
        @Override
        public String key(Object world) {
            if (world instanceof World w) {
                return w.getName();
            }
            return world != null ? world.toString() : "";
        }

        @Override
        public Object worldByKey(String key) {
            if (key == null || key.isBlank()) return null;
            return Bukkit.getWorld(key);
        }
    };

    @Override
    public Object normalize(Object loc) {
        if (loc instanceof Location l) {
            World w = l.getWorld();
            return new Location(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }
        return loc;
    }

    @Override
    public Object offset(Object loc, int dx, int dy, int dz) {
        if (loc instanceof Location l) {
            return new Location(l.getWorld(), l.getBlockX() + dx, l.getBlockY() + dy, l.getBlockZ() + dz);
        }
        return loc;
    }

    @Override
    public PipeNetworkManager.ChunkKey chunkKey(Object loc) {
        if (loc instanceof Chunk chunk) {
            return new PipeNetworkManager.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
        if (loc instanceof Location l && l.getWorld() != null) {
            return new PipeNetworkManager.ChunkKey(l.getWorld().getUID(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
        }
        return new PipeNetworkManager.ChunkKey(new UUID(0L, 0L), 0, 0);
    }

    @Override
    public boolean isChunkLoaded(Object loc) {
        if (loc instanceof Chunk chunk) {
            return chunk.isLoaded();
        }
        if (loc instanceof Location l && l.getWorld() != null) {
            Chunk chunk = l.getWorld().getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4);
            return chunk != null && chunk.isLoaded();
        }
        return false;
    }

    @Override
    public boolean isInventoryAt(Object loc) {
        if (!(loc instanceof Location l) || l.getWorld() == null) return false;
        Block block = l.getWorld().getBlockAt(l);
        return block.getState() instanceof InventoryHolder;
    }

    @Override
    public Object worldOf(Object loc) {
        if (loc instanceof Location l) return l.getWorld();
        return null;
    }

    @Override
    public WorldKeyAdapter<Object> worldKeyAdapter() {
        return WORLD_KEYS;
    }

    @Override
    public UUID worldId(Object loc) {
        if (loc instanceof Location l && l.getWorld() != null) {
            return l.getWorld().getUID();
        }
        return new UUID(0L, 0L);
    }

    @Override
    public int blockX(Object loc) {
        if (loc instanceof Location l) return l.getBlockX();
        return 0;
    }

    @Override
    public int blockY(Object loc) {
        if (loc instanceof Location l) return l.getBlockY();
        return 0;
    }

    @Override
    public int blockZ(Object loc) {
        if (loc instanceof Location l) return l.getBlockZ();
        return 0;
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        if (world instanceof World w) {
            return new Location(w, x, y, z);
        }
        return null;
    }
}
