package dev.cloudframe.bukkit.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import dev.cloudframe.common.platform.EntityRenderer;

import java.util.*;

/**
 * Bukkit implementation of EntityRenderer.
 */
public class BukkitEntityRenderer implements EntityRenderer {
    private final Map<String, Set<UUID>> entityMap = new HashMap<>();

    @Override
    public void spawnDisplayEntity(Object locationObj, Object itemStackObj) {
        if (!(locationObj instanceof Location location) || !(itemStackObj instanceof ItemStack itemStack)) return;
        World world = location.getWorld();
        if (world == null) return;
        Location center = location.clone().add(0.5, 0.5, 0.5);
        try {
            ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setItemStack(itemStack);
            trackEntity(locationKey(location), display.getUniqueId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void spawnInteractionEntity(Object locationObj, double width, double height) {
        if (!(locationObj instanceof Location location)) return;
        World world = location.getWorld();
        if (world == null) return;
        Location center = location.clone().add(0.5, 0.5, 0.5);
        try {
            Interaction interaction = (Interaction) world.spawnEntity(center, EntityType.INTERACTION);
            interaction.setInteractionHeight((float) height);
            interaction.setInteractionWidth((float) width);
            interaction.setInvulnerable(true);
            interaction.setPersistent(false);
            trackEntity(locationKey(location), interaction.getUniqueId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void removeEntitiesAt(Object locationObj) {
        if (!(locationObj instanceof Location location)) return;
        String key = locationKey(location);
        Set<UUID> ids = entityMap.remove(key);
        if (ids != null) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && !e.isDead()) e.remove();
            }
        }
    }

    @Override
    public List<Object> getActiveEntities() {
        List<Object> result = new ArrayList<>();
        for (Set<UUID> ids : entityMap.values()) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && !e.isDead()) result.add(e);
            }
        }
        return result;
    }

    @Override
    public void shutdown() {
        for (Set<UUID> ids : entityMap.values()) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && !e.isDead()) e.remove();
            }
        }
        entityMap.clear();
    }

    private void trackEntity(String key, UUID id) {
        entityMap.computeIfAbsent(key, k -> new HashSet<>()).add(id);
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
