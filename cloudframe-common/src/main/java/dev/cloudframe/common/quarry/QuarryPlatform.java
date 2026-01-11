package dev.cloudframe.common.quarry;

import java.util.List;
import java.util.UUID;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.ItemPacketDeliveryCallback;
import dev.cloudframe.common.platform.items.ItemStackKeyAdapter;
import dev.cloudframe.common.platform.world.LocationKeyAdapter;
import dev.cloudframe.common.platform.world.WorldKeyAdapter;
import dev.cloudframe.common.util.DirIndex;

/**
 * Platform abstraction for quarry operations.
 * Implemented by Bukkit/Fabric layers to handle blocks, inventories, and effects.
 */
public interface QuarryPlatform {
    Object normalize(Object loc);
    Object offset(Object loc, int dx, int dy, int dz);
    boolean isChunkLoaded(Object loc);
    boolean isRedstonePowered(Object loc);
    void setChunkForced(Object world, int chunkX, int chunkZ, boolean forced);
    boolean isMineable(Object loc);
    List<Object> getDrops(Object loc, boolean silkTouch);

    /**
     * Fortune-aware drops.
     *
     * <p>Default behavior ignores fortune and delegates to the legacy 2-arg method.</p>
     */
    default List<Object> getDrops(Object loc, boolean silkTouch, int fortuneLevel) {
        return getDrops(loc, silkTouch);
    }
    void setBlockAir(Object loc);
    void playBreakEffects(Object loc);
    void sendBlockCrack(Object loc, float progress01);
    boolean isInventory(Object loc);
    Object getInventoryHolder(Object loc);
    int addToInventory(Object inventoryHolder, Object itemStack);
    int totalRoomFor(Object inventoryHolder, Object itemStack);
    int emptySlotCount(Object inventoryHolder);
    LocationKeyAdapter<Object> locationKeyAdapter();
    ItemStackKeyAdapter<Object> itemKeyAdapter();
    double distanceSquared(Object a, Object b);
    Object createLocation(Object world, int x, int y, int z);
    Object worldOf(Object loc);
    /**
     * Serialize a world reference to a stable string for persistence.
     * Bukkit: typically world name.
     * Fabric: typically dimension id (e.g. "minecraft:the_nether").
     */
    WorldKeyAdapter<Object> worldKeyAdapter();
    int blockX(Object loc);
    int blockY(Object loc);
    int blockZ(Object loc);
    int stackAmount(Object itemStack);
    Object copyWithAmount(Object itemStack, int amount);
    int maxStackSize(Object itemStack);
    PipeNetworkManager pipes();
    ItemPacketManager packets();
    ItemPacketFactory packetFactory();

    /**
     * Serialize a world reference to a stable string for persistence.
     * Bukkit: typically world name.
     * Fabric: typically dimension id (e.g. "minecraft:the_nether").
     */
    default String worldName(Object world) {
        WorldKeyAdapter<Object> adapter = worldKeyAdapter();
        if (adapter == null) return "";
        try {
            String key = adapter.key(world);
            return key != null ? key : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    default Object worldByName(String name) {
        WorldKeyAdapter<Object> adapter = worldKeyAdapter();
        if (adapter == null) return null;
        try {
            return adapter.worldByKey(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Returns a stable, comparable key for {@code loc}.
     *
     * <p>Platforms should implement {@link #locationKeyAdapter()} and inherit this default.</p>
     */
    default String locationKey(Object loc) {
        LocationKeyAdapter<Object> adapter = locationKeyAdapter();
        if (adapter == null) return "null";
        try {
            String key = adapter.key(loc);
            return key != null ? key : "null";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /**
     * Returns a stable, count-independent key for {@code itemStack}.
     *
     * <p>Platforms should implement {@link #itemKeyAdapter()} and inherit this default.</p>
     */
    default String itemKey(Object itemStack) {
        ItemStackKeyAdapter<Object> adapter = itemKeyAdapter();
        if (adapter == null) return "null";
        try {
            String key = adapter.key(itemStack);
            return key != null ? key : "null";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /**
     * Convenience helper based on {@link #totalRoomFor(Object, Object)}.
     */
    default boolean hasSpaceFor(Object inventoryHolder, Object itemStack) {
        if (inventoryHolder == null || itemStack == null) return false;
        return totalRoomFor(inventoryHolder, itemStack) >= stackAmount(itemStack);
    }

    /**
     * Returns the 6-direction index from {@code fromLoc} to {@code toLoc} when {@code toLoc}
     * is directly adjacent by exactly one block along a single axis.
     *
     * <p>If the locations are not adjacent (or either is null), returns -1.</p>
     */
    default int dirIndexBetween(Object fromLoc, Object toLoc) {
        if (fromLoc == null || toLoc == null) return -1;

        int dx = blockX(toLoc) - blockX(fromLoc);
        int dy = blockY(toLoc) - blockY(fromLoc);
        int dz = blockZ(toLoc) - blockZ(fromLoc);
        return DirIndex.fromDelta(dx, dy, dz);
    }

    interface ItemPacketFactory {
        void send(Object itemStack, List<Object> waypoints, Object destinationInventory, DeliveryCallback callback);
    }

    interface DeliveryCallback extends ItemPacketDeliveryCallback {
    }

    UUID ownerFromPlayer(Object player);
    
    // Glass frame operations for visual quarry boundaries
    void placeGlassFrame(Object world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    void removeGlassFrame(Object world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    boolean isGlassFrameBlock(Object loc);

    /**
     * Pipe-side filter hook.
     *
     * Called during quarry output routing when selecting a destination inventory.
     * The {@code pipeLocAdjacentToInventory} is the pipe block that is directly touching the inventory,
     * and the filter (if any) is expected to be attached to the pipe face pointing into that inventory.
     */
    default boolean allowsPipeFilter(Object pipeLocAdjacentToInventory, Object inventoryLoc, Object itemStack) {
        return true;
    }

    /**
     * Fallback behavior when output routing cannot find any destination inventory that can accept an item.
     *
     * <p>If implemented, the platform should spawn/drop the item stack at (or near) the controller location,
     * and return true if the item was successfully dropped/consumed.</p>
     */
    default boolean dropItemAtController(Object controllerLoc, Object itemStack) {
        return false;
    }

    /**
     * Power system hooks (CFE).
     *
     * Default is disabled to preserve legacy behavior on platforms that don't implement power.
     */
    default boolean supportsPower() {
        return false;
    }

    /**
     * Attempt to extract power from the network connected to the given controller.
     *
     * @return how much power was actually extracted (0..amount)
     */
    default long extractPowerCfe(Object controllerLoc, long amount) {
        return 0L;
    }

    /**
     * Returns true when the controller is connected to a pipe network or adjacent inventory.
     *
     * Platforms may override this to provide more reliable discovery when the pipe network
     * cache can fall out of sync with in-world blocks.
     */
    default boolean hasValidOutput(Object controllerLoc) {
        Object ctrl = controllerLoc;
        if (ctrl == null) return false;

        // Direct adjacent inventory
        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            Object adj = offset(ctrl, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
            if (isInventory(adj)) return true;
        }

        // Pipe connectivity via pipes()
        PipeNetworkManager pipes = pipes();
        if (pipes == null) return false;

        // Find adjacent pipe
        dev.cloudframe.common.pipes.PipeNode start = null;
        for (int dirIndex = 0; dirIndex < 6; dirIndex++) {
            Object adj = offset(ctrl, DirIndex.dx(dirIndex), DirIndex.dy(dirIndex), DirIndex.dz(dirIndex));
            var node = pipes.getPipe(adj);
            if (node != null) {
                int towardController = DirIndex.opposite(dirIndex);
                if (towardController >= 0 && node.isInventorySideDisabled(towardController)) {
                    continue;
                }
                start = node;
                break;
            }
        }
        if (start == null) return false;

        List<Object> inventories = pipes.findInventoriesNear(start);
        return inventories != null && !inventories.isEmpty();
    }
}
