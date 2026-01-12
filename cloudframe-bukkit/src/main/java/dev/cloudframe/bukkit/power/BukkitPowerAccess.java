package dev.cloudframe.bukkit.power;

import org.bukkit.Location;

/**
 * Bukkit-side adapter for CloudFrame power blocks.
 *
 * <p>This interface is intentionally small and keeps all block/entity specifics
 * on the Bukkit side. The shared traversal/caching logic lives in common
 * {@code dev.cloudframe.common.power.PowerNetworkManager}.</p>
 */
public interface BukkitPowerAccess {

    boolean isCable(Location loc);

    default boolean isCableSideDisabled(Location cableLoc, int dirIndex) {
        return false;
    }

    boolean isProducer(Location loc);

    long producerCfePerTick(Location producerLoc);

    boolean isCell(Location loc);

    long cellInsertCfe(Location cellLoc, long amount);

    long cellExtractCfe(Location cellLoc, long amount);

    long cellStoredCfe(Location cellLoc);

    BukkitPowerAccess NOOP = new BukkitPowerAccess() {
        @Override public boolean isCable(Location loc) { return false; }
        @Override public boolean isProducer(Location loc) { return false; }
        @Override public long producerCfePerTick(Location producerLoc) { return 0L; }
        @Override public boolean isCell(Location loc) { return false; }
        @Override public long cellInsertCfe(Location cellLoc, long amount) { return 0L; }
        @Override public long cellExtractCfe(Location cellLoc, long amount) { return 0L; }
        @Override public long cellStoredCfe(Location cellLoc) { return 0L; }
    };
}
