package dev.cloudframe.bukkit.power;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import dev.cloudframe.common.power.PowerNetworkManager;

/**
 * Thin Bukkit wrapper around the shared {@link PowerNetworkManager}.
 *
 * <p>By default this is inert unless you provide a real {@link BukkitPowerAccess}
 * that can identify cables/producers/cells in the Bukkit world.</p>
 */
public final class BukkitPowerNetworkAdapter {

    private final BukkitPowerAccess bukkitAccess;
    private final PowerNetworkManager manager;

    public BukkitPowerNetworkAdapter(BukkitPowerAccess access) {
        this.bukkitAccess = access != null ? access : BukkitPowerAccess.NOOP;

        this.manager = new PowerNetworkManager(
            new PowerNetworkManager.LocationAdapter() {
                @Override
                public Object normalize(Object loc) {
                    if (!(loc instanceof Location l)) return null;
                    World w = l.getWorld();
                    if (w == null) return null;
                    return new Location(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
                }

                @Override
                public Object offset(Object loc, int dx, int dy, int dz) {
                    if (!(loc instanceof Location l)) return null;
                    World w = l.getWorld();
                    if (w == null) return null;
                    return new Location(w, l.getBlockX() + dx, l.getBlockY() + dy, l.getBlockZ() + dz);
                }

                @Override
                public String worldId(Object loc) {
                    if (!(loc instanceof Location l)) return null;
                    World w = l.getWorld();
                    if (w == null) return null;
                    // Stable enough for persistence within Bukkit: world name.
                    return w.getName();
                }

                @Override
                public int blockX(Object loc) {
                    return loc instanceof Location l ? l.getBlockX() : 0;
                }

                @Override
                public int blockY(Object loc) {
                    return loc instanceof Location l ? l.getBlockY() : 0;
                }

                @Override
                public int blockZ(Object loc) {
                    return loc instanceof Location l ? l.getBlockZ() : 0;
                }
            },
            new PowerNetworkManager.Access() {
                @Override
                public boolean isCable(Object ctx, Object loc) {
                    return (loc instanceof Location l) && bukkitAccess.isCable(l);
                }

                @Override
                public boolean isCableSideDisabled(Object ctx, Object cableLoc, int dirIndex) {
                    return (cableLoc instanceof Location l) && bukkitAccess.isCableSideDisabled(l, dirIndex);
                }

                @Override
                public boolean isProducer(Object ctx, Object loc) {
                    return (loc instanceof Location l) && bukkitAccess.isProducer(l);
                }

                @Override
                public long producerCfePerTick(Object ctx, Object producerLoc) {
                    return (producerLoc instanceof Location l) ? bukkitAccess.producerCfePerTick(l) : 0L;
                }

                @Override
                public boolean isCell(Object ctx, Object loc) {
                    return (loc instanceof Location l) && bukkitAccess.isCell(l);
                }

                @Override
                public long cellInsertCfe(Object ctx, Object cellLoc, long amount) {
                    if (amount <= 0L) return 0L;
                    return (cellLoc instanceof Location l) ? bukkitAccess.cellInsertCfe(l, amount) : 0L;
                }

                @Override
                public long cellExtractCfe(Object ctx, Object cellLoc, long amount) {
                    if (amount <= 0L) return 0L;
                    return (cellLoc instanceof Location l) ? bukkitAccess.cellExtractCfe(l, amount) : 0L;
                }

                @Override
                public long cellStoredCfe(Object ctx, Object cellLoc) {
                    return (cellLoc instanceof Location l) ? bukkitAccess.cellStoredCfe(l) : 0L;
                }
            }
        );
    }

    public void beginTick(long tick) {
        manager.beginTick(null, tick);
    }

    public void endTick() {
        manager.endTick(null);
    }

    public long extractPowerCfe(Location controllerLoc, long amount) {
        return manager.extractPowerCfe(null, controllerLoc, amount);
    }

    public long extractGenerationOnlyCfe(Location controllerLoc, long amount) {
        return manager.extractGenerationOnlyCfe(null, controllerLoc, amount);
    }

    public PowerNetworkManager.NetworkInfo measureNetwork(Location controllerLoc) {
        return manager.measureNetwork(null, controllerLoc);
    }

    public PowerNetworkManager.CableProbeInfo measureNetworkForProbe(Location controllerLoc) {
        return manager.measureNetworkForProbe(null, controllerLoc);
    }

    /** Convenience for platforms that can access the server tick counter. */
    public void beginTickFromServer() {
        try {
            beginTick(Bukkit.getServer().getCurrentTick());
        } catch (Throwable ignored) {
            // Not all Bukkit implementations expose current tick. Caller can use beginTick(long).
        }
    }
}
