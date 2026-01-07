package dev.cloudframe.common.quarry;

import java.util.Map;

/**
 * Shared helper for quarry output in-flight accounting.
 *
 * <p>Tracks how many items are currently en route to a given destination inventory location,
 * keyed by {@link QuarryPlatform#locationKey(Object)}. Selection helpers can use this to avoid
 * over-committing inventory capacity when multiple packets are in transit.</p>
 */
public final class InFlightAccounting {

    private InFlightAccounting() {
    }

    public static boolean canReserveDestination(
            QuarryPlatform platform,
            Object destinationInventoryLocation,
            Object destinationInventoryHolder,
            Object itemStack,
            Map<String, Integer> inFlightByDestination,
            Map<String, Integer> inFlightByDestinationAndItem
    ) {
        if (platform == null || destinationInventoryLocation == null || destinationInventoryHolder == null) return false;
        if (itemStack == null) return false;

        int needed = platform.stackAmount(itemStack);
        if (needed <= 0) return false;

        int maxPerStack = Math.max(1, platform.maxStackSize(itemStack));
        int emptySlots = Math.max(0, platform.emptySlotCount(destinationInventoryHolder));
        int emptyRoom;
        try {
            long er = (long) emptySlots * (long) maxPerStack;
            emptyRoom = er > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) er;
        } catch (Throwable ignored) {
            emptyRoom = Integer.MAX_VALUE;
        }

        int totalRoom = Math.max(0, platform.totalRoomFor(destinationInventoryHolder, itemStack));
        int mergeableRoom = Math.max(0, totalRoom - emptyRoom);

        String destKey = platform.locationKey(destinationInventoryLocation);
        int reservedTotal = getOrZero(inFlightByDestination, destKey);
        int reservedSame = getOrZero(inFlightByDestinationAndItem, compositeKey(platform, destKey, itemStack));

        // Other item types can't consume mergeableRoom, but they do consume empty slots.
        int reservedOther = Math.max(0, reservedTotal - reservedSame);
        int sameSpillToEmpty = Math.max(0, reservedSame - mergeableRoom);
        int emptyConsumed = reservedOther + sameSpillToEmpty;

        int remainingMergeable = Math.max(0, mergeableRoom - reservedSame);
        int remainingEmpty = Math.max(0, emptyRoom - emptyConsumed);
        int available = remainingMergeable + remainingEmpty;

        return available >= needed;
    }

    public static void reserve(
            Map<String, Integer> inFlightByDestination,
            Map<String, Integer> inFlightByDestinationAndItem,
            QuarryPlatform platform,
            Object destinationInventoryLocation,
            Object itemStack
    ) {
        if (inFlightByDestination == null || platform == null || destinationInventoryLocation == null) return;
        if (itemStack == null) return;

        int amount = platform.stackAmount(itemStack);
        if (amount <= 0) return;

        String destKey = platform.locationKey(destinationInventoryLocation);
        inFlightByDestination.merge(destKey, amount, Integer::sum);

        if (inFlightByDestinationAndItem != null) {
            inFlightByDestinationAndItem.merge(compositeKey(platform, destKey, itemStack), amount, Integer::sum);
        }
    }

    public static void release(
            Map<String, Integer> inFlightByDestination,
            Map<String, Integer> inFlightByDestinationAndItem,
            QuarryPlatform platform,
            Object destinationInventoryLocation,
            Object itemStack,
            int deliveredAmount
    ) {
        if (inFlightByDestination == null || platform == null || destinationInventoryLocation == null) return;
        if (itemStack == null) return;
        if (deliveredAmount <= 0) return;

        String destKey = platform.locationKey(destinationInventoryLocation);
        inFlightByDestination.compute(destKey, (k, current) -> {
            if (current == null) return null;
            int remaining = current - deliveredAmount;
            return remaining > 0 ? remaining : null;
        });

        if (inFlightByDestinationAndItem != null) {
            String key = compositeKey(platform, destKey, itemStack);
            inFlightByDestinationAndItem.compute(key, (k, current) -> {
                if (current == null) return null;
                int remaining = current - deliveredAmount;
                return remaining > 0 ? remaining : null;
            });
        }
    }

    private static String compositeKey(QuarryPlatform platform, String destinationKey, Object itemStack) {
        String itemKey = platform.itemKey(itemStack);
        if (itemKey == null) itemKey = "null";
        return destinationKey + "|" + itemKey;
    }

    private static int getOrZero(Map<String, Integer> map, String key) {
        if (map == null || map.isEmpty()) return 0;
        Integer v = map.get(key);
        return v != null ? v : 0;
    }
}