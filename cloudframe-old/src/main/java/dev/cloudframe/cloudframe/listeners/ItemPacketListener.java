package dev.cloudframe.cloudframe.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;

/**
 * Prevent packet-visual item entities from being picked up, merged, or despawned.
 */
public class ItemPacketListener implements Listener {

    private final NamespacedKey key;

    public ItemPacketListener() {
        // CloudFrameRegistry.plugin() is set during onEnable before listeners are registered.
        this.key = new NamespacedKey(CloudFrameRegistry.plugin(), "cloudframe_packet_item");
    }

    private boolean isPacketItem(Item item) {
        return item != null && item.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent e) {
        if (isPacketItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent e) {
        if (isPacketItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent e) {
        if (isPacketItem(e.getEntity()) || isPacketItem(e.getTarget())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent e) {
        if (isPacketItem(e.getEntity())) {
            e.setCancelled(true);
        }
    }
}
