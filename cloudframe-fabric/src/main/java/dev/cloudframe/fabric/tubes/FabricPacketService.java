package dev.cloudframe.fabric.tubes;

import dev.cloudframe.common.quarry.QuarryPlatform;
import dev.cloudframe.common.tubes.ItemPacket;
import dev.cloudframe.common.tubes.ItemPacketManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper service for creating and managing item packets in Fabric.
 */
public class FabricPacketService {

    private final ItemPacketManager packetManager;
    private final ServerWorld world;

    public FabricPacketService(ItemPacketManager packetManager, ServerWorld world) {
        this.packetManager = packetManager;
        this.world = world;
    }

    public void enqueue(ItemStack itemStack, List<BlockPos> waypoints, BlockPos destinationInventory, QuarryPlatform.DeliveryCallback callback) {
        List<Object> waypointObjects = new ArrayList<>();
        for (BlockPos pos : waypoints) {
            waypointObjects.add(pos);
        }

        ItemPacket packet = new ItemPacket(
            itemStack,
            waypointObjects,
            destinationInventory,
            new FabricPacketVisuals(world),
            new FabricItemStackAdapter()
        );

        packetManager.add(packet);
    }
}
