package dev.cloudframe.common.pipes;

import java.util.function.IntFunction;

/**
 * Shared delivery helpers for inserting an item into an inventory at or near a location.
 *
 * <p>This intentionally lives in common and uses only the platform-provided
 * {@link ItemPacketManager.IItemDeliveryProvider} abstraction.</p>
 */
public final class AdjacentInventoryDelivery {

    private AdjacentInventoryDelivery() {
    }

    /**
     * Deliver to a known inventory block location.
     *
     * @return number of items actually inserted
     */
    public static int deliverToInventoryLocation(
            Object inventoryBlockLocation,
            Object item,
            int itemAmount,
            ItemPacketManager.IItemDeliveryProvider provider,
            IntFunction<Object> leftoverFactory,
            ItemPacketDeliveryCallback onDelivered
    ) {
        if (inventoryBlockLocation == null || provider == null || item == null) return 0;

        if (!provider.isChunkLoaded(inventoryBlockLocation)) {
            provider.dropItems(inventoryBlockLocation, new Object[]{item});
            return 0;
        }

        Object holder = provider.getInventoryHolder(inventoryBlockLocation);
        if (holder == null) {
            provider.dropItems(inventoryBlockLocation, new Object[]{item});
            return 0;
        }

        int actuallyAdded = provider.addItem(holder, item);
        int leftovers = Math.max(0, itemAmount - actuallyAdded);

        if (leftovers > 0 && leftoverFactory != null) {
            provider.dropItems(inventoryBlockLocation, new Object[]{leftoverFactory.apply(leftovers)});
        }

        if (onDelivered != null) onDelivered.delivered(inventoryBlockLocation, item, actuallyAdded);

        return actuallyAdded;
    }

    /**
     * Scan the 6 adjacent blocks around {@code baseLocation} and attempt to insert into the first
     * inventory that accepts at least 1 item.
     *
     * <p>If an inventory is found but only accepts some items, leftovers are dropped at that
     * inventory block location.</p>
     *
     * @return number of items actually inserted
     */
    public static int deliverToAnyAdjacentInventory(
            Object baseLocation,
            Object item,
            int itemAmount,
            ItemPacketManager.IItemDeliveryProvider provider,
            IntFunction<Object> leftoverFactory,
            ItemPacketDeliveryCallback onDelivered
    ) {
        if (baseLocation == null || provider == null || item == null) return 0;

        if (!provider.isChunkLoaded(baseLocation)) {
            provider.dropItems(baseLocation, new Object[]{item});
            return 0;
        }

        for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
            Object adj = provider.getAdjacentBlockLocation(baseLocation, dirIdx);
            if (adj == null) continue;

            Object holder = provider.getInventoryHolder(adj);
            if (holder == null) continue;

            int actuallyAdded = provider.addItem(holder, item);
            if (actuallyAdded <= 0) {
                continue;
            }

            int leftovers = Math.max(0, itemAmount - actuallyAdded);
            if (leftovers > 0 && leftoverFactory != null) {
                provider.dropItems(adj, new Object[]{leftoverFactory.apply(leftovers)});
            }

            if (onDelivered != null) onDelivered.delivered(adj, item, actuallyAdded);

            return actuallyAdded;
        }

        provider.dropItems(baseLocation, new Object[]{item});
        return 0;
    }
}
