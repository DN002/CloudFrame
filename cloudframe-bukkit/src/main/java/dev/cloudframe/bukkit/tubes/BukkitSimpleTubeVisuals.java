package dev.cloudframe.bukkit.tubes;

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

import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.common.tubes.TubeNetworkManager.ITubeVisuals;

/**
 * Lightweight Bukkit visuals for tubes: spawns a single Interaction hitbox per tube.
 * This preserves client-side selection without placing blocks.
 */
public class BukkitSimpleTubeVisuals implements ITubeVisuals {

    private final JavaPlugin plugin;
    private final TubeNetworkManager tubeManager;
    private final BukkitTubeLocationAdapter locations;
    private final Map<Location, UUID> interactions = new HashMap<>();

    private static final int[][] DIRS = new int[][] {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    public BukkitSimpleTubeVisuals(JavaPlugin plugin, TubeNetworkManager tubeManager, BukkitTubeLocationAdapter locations) {
        this.plugin = plugin;
        this.tubeManager = tubeManager;
        this.locations = locations;
    }

    @Override
    public void updateTubeAndNeighbors(Object locObj) {
        Location loc = toLoc(locObj);
        if (loc == null) return;
        updateTube(loc);
        for (int[] d : DIRS) {
            Location adj = new Location(loc.getWorld(), loc.getBlockX() + d[0], loc.getBlockY() + d[1], loc.getBlockZ() + d[2]);
            updateTube(adj);
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

    private void updateTube(Location loc) {
        loc = normalize(loc);
        if (loc == null) return;

        // Remove visuals if tube missing
        if (tubeManager.getTube(loc) == null) {
            remove(loc);
            return;
        }

        // Ensure chunk is loaded before spawning
        Chunk chunk = loc.getChunk();
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }

        UUID existing = interactions.get(loc);
        if (existing != null) {
            Entity e = plugin.getServer().getEntity(existing);
            if (e instanceof Interaction interaction && !interaction.isDead()) {
                return; // already present
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
