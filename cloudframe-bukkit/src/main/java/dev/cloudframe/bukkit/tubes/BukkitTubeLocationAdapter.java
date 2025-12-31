package dev.cloudframe.bukkit.tubes;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;

import dev.cloudframe.common.tubes.TubeNetworkManager;

/**
 * Bukkit implementation of TubeNetworkManager.ILocationAdapter.
 */
public class BukkitTubeLocationAdapter implements TubeNetworkManager.ILocationAdapter {

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
    public TubeNetworkManager.ChunkKey chunkKey(Object loc) {
        if (loc instanceof Chunk chunk) {
            return new TubeNetworkManager.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
        if (loc instanceof Location l && l.getWorld() != null) {
            return new TubeNetworkManager.ChunkKey(l.getWorld().getUID(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
        }
        return new TubeNetworkManager.ChunkKey(new UUID(0L, 0L), 0, 0);
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
    public String worldName(Object loc) {
        if (loc instanceof Location l && l.getWorld() != null) {
            return l.getWorld().getName();
        }
        return "";
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
    public Object worldByName(String name) {
        return Bukkit.getWorld(name);
    }

    @Override
    public Object createLocation(Object world, int x, int y, int z) {
        if (world instanceof World w) {
            return new Location(w, x, y, z);
        }
        return null;
    }
}
