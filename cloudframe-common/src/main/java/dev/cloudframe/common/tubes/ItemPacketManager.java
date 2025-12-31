package dev.cloudframe.common.tubes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;

/**
 * Platform-agnostic item packet manager.
 * Coordinates movement and delivery of items through tube networks.
 */
public class ItemPacketManager {

    private static final Debug debug = DebugManager.get(ItemPacketManager.class);

    private final List<ItemPacket> packets = new ArrayList<>();
    private final IItemDeliveryProvider deliveryProvider;

    // Shared 6-direction vectors
    private static final int[][] DIRS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    /**
     * Provider interface for platform-specific delivery operations.
     * Implemented by BukkitItemDeliveryProvider, FabricItemDeliveryProvider, etc.
     */
    public interface IItemDeliveryProvider {
        /**
         * Check if chunk containing the location is loaded.
         * @param location location object (Object for platform independence)
         * @return true if chunk is loaded
         */
        boolean isChunkLoaded(Object location);

        /**
         * Get inventory holder at the specified block location.
         * @param blockLocation block location
         * @return inventory holder object, or null
         */
        Object getInventoryHolder(Object blockLocation);

        /**
         * Add item to an inventory.
         * @param inventoryHolder holder object
         * @param item item to add
         * @return amount actually added
         */
        int addItem(Object inventoryHolder, Object item);

        /**
         * Drop items near a location.
         * @param location drop location
         * @param items items to drop (Object array for platform independence)
         */
        void dropItems(Object location, Object[] items);

        /**
         * Get block at location adjacent by direction vector.
         * @param baseLocation base location
         * @param dirIndex direction index (0-5 for the 6 directions)
         * @return block location Object, or null if invalid
         */
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

            if (!deliveryProvider.isChunkLoaded(destInvLoc)) {
                if (shouldLog) {
                    debug.log("deliver", "Chunk not loaded — dropping item safely");
                }
                deliveryProvider.dropItems(destInvLoc, new Object[]{p.getItem()});
                return;
            }

            Object holder = deliveryProvider.getInventoryHolder(destInvLoc);
            if (holder != null) {
                int deliveredAmount = p.getItemAmount();
                int actuallyAdded = deliveryProvider.addItem(holder, p.getItem());
                int leftovers = deliveredAmount - actuallyAdded;

                // If there were leftovers, drop them
                if (leftovers > 0) {
                    Object leftoverItem = p.createLeftoverItem(leftovers);
                    deliveryProvider.dropItems(destInvLoc, new Object[]{leftoverItem});
                }

                // Notify callback of delivery
                if (p.getOnDeliveryCallback() != null) {
                    p.getOnDeliveryCallback().accept(destInvLoc, actuallyAdded);
                }
                return;
            }

            // Inventory missing; drop.
            deliveryProvider.dropItems(destInvLoc, new Object[]{p.getItem()});
            return;
        }

        // Fallback: treat last waypoint as a tube location and insert into any adjacent inventory.
        Object loc = p.getLastWaypoint();

        if (shouldLog) {
            debug.log("deliver", "Delivering to tube, scanning adjacent blocks");
        }

        // Ensure destination chunk is loaded
        if (!deliveryProvider.isChunkLoaded(loc)) {
            if (shouldLog) {
                debug.log("deliver", "Chunk not loaded — dropping item safely");
            }
            deliveryProvider.dropItems(loc, new Object[]{p.getItem()});
            return;
        }

        // Check all 6 sides for an inventory
        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            Object adj = deliveryProvider.getAdjacentBlockLocation(loc, dirIdx);
            if (adj == null) continue;

            Object inv = deliveryProvider.getInventoryHolder(adj);
            if (inv != null) {
                if (shouldLog) {
                    debug.log("deliver", "Found inventory at adjacent block");
                }
                deliveryProvider.addItem(inv, p.getItem());
                return;
            }
        }

        // Fallback: drop item
        if (shouldLog) {
            debug.log("deliver", "No inventory found — dropping item");
        }
        deliveryProvider.dropItems(loc, new Object[]{p.getItem()});
    }
}
