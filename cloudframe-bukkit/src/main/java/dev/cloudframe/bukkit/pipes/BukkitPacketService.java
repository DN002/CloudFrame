package dev.cloudframe.bukkit.pipes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.common.pipes.ItemPacket;
import dev.cloudframe.common.pipes.ItemPacketDeliveryCallback;
import dev.cloudframe.common.pipes.ItemPacketManager;

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

    public void enqueue(ItemStack item, List<Location> waypoints, Location destinationInventory, ItemPacketDeliveryCallback onDelivery) {
        if (item == null || waypoints == null || waypoints.size() < 2) return;

        List<Object> points = new ArrayList<>(waypoints.size());
        points.addAll(waypoints);

        ItemPacket packet = new ItemPacket(item, List.copyOf(points), destinationInventory, onDelivery, visuals, itemAdapter);
        packetManager.add(packet);
    }

    public void enqueue(ItemStack item, List<Location> waypoints, Location destinationInventory) {
        enqueue(item, waypoints, destinationInventory, null);
    }
}
