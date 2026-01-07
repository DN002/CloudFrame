package dev.cloudframe.common.quarry;

import java.util.Map;

import dev.cloudframe.common.util.DirIndex;

/**
 * Shared helper for selecting an adjacent inventory destination.
 *
 * <p>This is used by quarry output routing (and is intended to be reusable by
 * future platforms like Bukkit) so selection semantics stay consistent.</p>
 */
public final class AdjacentInventorySelection {

    private AdjacentInventorySelection() {
    }

    /**
     * Returns the location of the first adjacent inventory that currently has space for the
     * provided item stack, or {@code null} if none qualify.
     */
    public static Object findFirstAdjacentInventoryWithSpace(
            QuarryPlatform platform,
            Object baseLocation,
            Object itemStack,
            Map<String, Integer> inFlightByDestination,
            Map<String, Integer> inFlightByDestinationAndItem
    ) {
        if (platform == null || baseLocation == null || itemStack == null) return null;

        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            Object adj = platform.offset(baseLocation, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
            if (!platform.isInventory(adj)) continue;

            Object holder = platform.getInventoryHolder(adj);
            if (holder == null) continue;
            if (!InFlightAccounting.canReserveDestination(platform, adj, holder, itemStack, inFlightByDestination, inFlightByDestinationAndItem)) continue;

            return adj;
        }

        return null;
    }
}
