package dev.cloudframe.common.quarry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.pipes.PipeNetworkManager;
import dev.cloudframe.common.pipes.ItemPacketManager;

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
    void setBlockAir(Object loc);
    void playBreakEffects(Object loc);
    void sendBlockCrack(Object loc, float progress01);
    boolean isInventory(Object loc);
    Object getInventoryHolder(Object loc);
    int addToInventory(Object inventoryHolder, Object itemStack);
    boolean hasSpaceFor(Object inventoryHolder, Object itemStack, Map<String, Integer> inFlight);
    String locationKey(Object loc);
    double distanceSquared(Object a, Object b);
    Object createLocation(Object world, int x, int y, int z);
    Object worldOf(Object loc);
    Object worldByName(String name);
    /**
     * Serialize a world reference to a stable string for persistence.
     * Bukkit: typically world name.
     * Fabric: typically dimension id (e.g. "minecraft:the_nether").
     */
    String worldName(Object world);
    int blockX(Object loc);
    int blockY(Object loc);
    int blockZ(Object loc);
    int stackAmount(Object itemStack);
    Object copyWithAmount(Object itemStack, int amount);
    int maxStackSize(Object itemStack);
    PipeNetworkManager pipes();
    ItemPacketManager packets();
    ItemPacketFactory packetFactory();

    interface ItemPacketFactory {
        void send(Object itemStack, List<Object> waypoints, Object destinationInventory, DeliveryCallback callback);
    }

    interface DeliveryCallback {
        void delivered(Object destination, int amount);
    }

    UUID ownerFromPlayer(Object player);
    
    // Glass frame operations for visual quarry boundaries
    void placeGlassFrame(Object world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    void removeGlassFrame(Object world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    boolean isGlassFrameBlock(Object loc);

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

        final int[][] dirs = new int[][] {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };

        // Direct adjacent inventory
        for (int[] dir : dirs) {
            Object adj = offset(ctrl, dir[0], dir[1], dir[2]);
            if (isInventory(adj)) return true;
        }

        // Pipe connectivity via pipes()
        PipeNetworkManager pipes = pipes();
        if (pipes == null) return false;

        // Find adjacent pipe
        dev.cloudframe.common.pipes.PipeNode start = null;
        for (int[] dir : dirs) {
            Object adj = offset(ctrl, dir[0], dir[1], dir[2]);
            var node = pipes.getPipe(adj);
            if (node != null) { start = node; break; }
        }
        if (start == null) return false;

        List<Object> inventories = pipes.findInventoriesNear(start);
        return inventories != null && !inventories.isEmpty();
    }
}
