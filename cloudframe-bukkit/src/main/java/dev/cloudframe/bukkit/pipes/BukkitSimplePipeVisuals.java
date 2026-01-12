package dev.cloudframe.bukkit.pipes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.java.JavaPlugin;

import dev.cloudframe.common.pipes.PipeNetworkManager;

/**
 * Lightweight Bukkit visuals for pipes: spawns a single Interaction hitbox per pipe.
 * This preserves client-side selection without placing blocks.
 */
public class BukkitSimplePipeVisuals implements PipeNetworkManager.IPipeVisuals {

    private final JavaPlugin plugin;
    private final PipeNetworkManager pipeManager;
    private final BukkitPipeLocationAdapter locations;
    private final Map<Location, UUID> interactions = new HashMap<>();

    public BukkitSimplePipeVisuals(JavaPlugin plugin, PipeNetworkManager pipeManager, BukkitPipeLocationAdapter locations) {
        this.plugin = plugin;
        this.pipeManager = pipeManager;
        this.locations = locations;
    }

    @Override
    public void updatePipeAndNeighbors(Object locObj) {
        Location loc = toLoc(locObj);
        if (loc == null) return;
        updatePipe(loc);
        for (int[] d : PipeNetworkManager.DIRS) {
            Location adj = new Location(loc.getWorld(), loc.getBlockX() + d[0], loc.getBlockY() + d[1], loc.getBlockZ() + d[2]);
            updatePipe(adj);
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : interactions.values()) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        interactions.clear();
    }

    private void updatePipe(Location loc) {
        loc = normalize(loc);
        if (loc == null) return;

        if (pipeManager.getPipe(loc) == null) {
            remove(loc);
            return;
        }

        Chunk chunk = loc.getChunk();
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }

        UUID existing = interactions.get(loc);
        if (existing != null) {
            Entity e = plugin.getServer().getEntity(existing);
            if (e instanceof Interaction interaction && !interaction.isDead()) {
                return;
            }
            interactions.remove(loc);
        }

        Interaction interaction = spawnInteraction(loc);
        if (interaction != null) {
            interactions.put(loc, interaction.getUniqueId());
        }
    }

    private Interaction spawnInteraction(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        try {
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            Interaction interaction = (Interaction) world.spawnEntity(center, EntityType.INTERACTION);
            interaction.setGravity(false);
            interaction.setPersistent(false);
            interaction.setInvulnerable(true);
            interaction.setInteractionWidth(0.0f);
            interaction.setInteractionHeight(0.0f);
            return interaction;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void remove(Location loc) {
        UUID id = interactions.remove(loc);
        if (id == null) return;
        Entity e = plugin.getServer().getEntity(id);
        if (e != null) {
            e.remove();
        }
    }

    private Location normalize(Object locObj) {
        Object norm = locations.normalize(locObj);
        if (norm instanceof Location l) return l;
        return null;
    }

    private Location toLoc(Object locObj) {
        if (locObj instanceof Location l) return normalize(l);
        return normalize(locObj);
    }
}
