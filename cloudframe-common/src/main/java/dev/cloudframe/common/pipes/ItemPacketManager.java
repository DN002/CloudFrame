package dev.cloudframe.common.pipes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;

/**
 * Platform-agnostic item packet manager.
 * Coordinates movement and delivery of items through pipe networks.
 */
public class ItemPacketManager {

    private static final Debug debug = DebugManager.get(ItemPacketManager.class);

    private final List<ItemPacket> packets = new ArrayList<>();
    private final IItemDeliveryProvider deliveryProvider;

    /**
     * Provider interface for platform-specific delivery operations.
     * Implemented by BukkitItemDeliveryProvider, FabricItemDeliveryProvider, etc.
     */
    public interface IItemDeliveryProvider {
        boolean isChunkLoaded(Object location);
        Object getInventoryHolder(Object blockLocation);
        int addItem(Object inventoryHolder, Object item);
        void dropItems(Object location, Object[] items);
        Object getAdjacentBlockLocation(Object baseLocation, int dirIndex);
    }

    public ItemPacketManager(IItemDeliveryProvider deliveryProvider) {
        this.deliveryProvider = deliveryProvider;
    }

    public void add(ItemPacket packet) {
        packets.add(packet);
        debug.log("add", "Added packet pathLength=" + packet.getPathLength());
    }

    public void tick(boolean shouldLog) {
        if (shouldLog) {
            debug.log("tick", "Ticking " + packets.size() + " packets");
        }

        Iterator<ItemPacket> it = packets.iterator();

        while (it.hasNext()) {
            ItemPacket p = it.next();

            try {
                boolean finished = p.tick(shouldLog);

                if (shouldLog) {
                    debug.log("tick", "Packet progress=" + String.format("%.2f", p.getProgress()) +
                            " finished=" + finished);
                }

                if (finished) {
                    if (shouldLog) {
                        debug.log("tick", "Packet finished");
                    }
                    deliver(p, shouldLog);
                    p.destroy();
                    it.remove();
                }

            } catch (Exception ex) {
                debug.log("tick", "Exception ticking packet: " + ex.getMessage());
                ex.printStackTrace();
                it.remove();
            }
        }
    }

    private void deliver(ItemPacket p, boolean shouldLog) {
        Object destInvLoc = p.getDestinationInventory();

        // If we know the destination inventory block, deliver directly there.
        if (destInvLoc != null) {
            if (shouldLog) {
                debug.log("deliver", "Delivering into known inventory");
            }

            AdjacentInventoryDelivery.deliverToInventoryLocation(
                    destInvLoc,
                    p.getItem(),
                    p.getItemAmount(),
                    deliveryProvider,
                    (leftovers) -> p.createLeftoverItem(leftovers),
                    p.getOnDeliveryCallback()
            );
            return;
        }

        // Fallback: treat last waypoint as a pipe location and insert into any adjacent inventory.
        Object loc = p.getLastWaypoint();

        if (shouldLog) {
            debug.log("deliver", "Delivering to pipe, scanning adjacent blocks");
        }

        int inserted = AdjacentInventoryDelivery.deliverToAnyAdjacentInventory(
                loc,
                p.getItem(),
                p.getItemAmount(),
                deliveryProvider,
                (leftovers) -> p.createLeftoverItem(leftovers),
                p.getOnDeliveryCallback()
        );

        if (shouldLog) {
            debug.log("deliver", inserted > 0 ? "Delivered into adjacent inventory" : "No inventory accepted item — dropped");
        }
    }
}
