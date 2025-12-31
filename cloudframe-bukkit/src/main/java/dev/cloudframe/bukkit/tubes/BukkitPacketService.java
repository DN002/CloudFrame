package dev.cloudframe.bukkit.tubes;

import java.util.List;
import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.common.tubes.ItemPacket;
import dev.cloudframe.common.tubes.ItemPacketManager;

/**
 * Helper to create and enqueue item packets with Bukkit visuals/adapters.
 */
public class BukkitPacketService {

    private final ItemPacketManager packetManager;
    private final BukkitPacketVisuals visuals;
    private final BukkitItemStackAdapter itemAdapter;

    public BukkitPacketService(ItemPacketManager packetManager, BukkitPacketVisuals visuals, BukkitItemStackAdapter itemAdapter) {
        this.packetManager = packetManager;
        this.visuals = visuals;
        this.itemAdapter = itemAdapter;
    }

    public void enqueue(ItemStack item, List<Location> waypoints, Location destinationInventory, BiConsumer<Location, Integer> onDelivery) {
        if (item == null || waypoints == null || waypoints.size() < 2) return;
        BiConsumer<Object, Integer> callback = onDelivery != null ? (loc, amt) -> onDelivery.accept((Location) loc, amt) : null;
        ItemPacket packet = new ItemPacket(item, List.copyOf(waypoints), destinationInventory, callback, visuals, itemAdapter);
        packetManager.add(packet);
    }

    public void enqueue(ItemStack item, List<Location> waypoints, Location destinationInventory) {
        enqueue(item, waypoints, destinationInventory, null);
    }
}
