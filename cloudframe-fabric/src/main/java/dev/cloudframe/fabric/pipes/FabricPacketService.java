package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.pipes.ItemPacket;
import dev.cloudframe.common.pipes.ItemPacketManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper service for creating and managing item packets in Fabric.
 */
public class FabricPacketService {

    private final ItemPacketManager packetManager;
    private final MinecraftServer server;

    public FabricPacketService(ItemPacketManager packetManager, MinecraftServer server) {
        this.packetManager = packetManager;
        this.server = server;
    }

    public void enqueue(ItemStack itemStack, List<Object> waypoints, Object destinationInventory, QuarryPlatform.DeliveryCallback callback) {
        List<Object> waypointObjects = new ArrayList<>(waypoints);

        ItemPacket packet = new ItemPacket(
            itemStack,
            waypointObjects,
            destinationInventory,
            new FabricPacketVisuals(server),
            new FabricItemStackAdapter()
        );

        packetManager.add(packet);
    }
}
