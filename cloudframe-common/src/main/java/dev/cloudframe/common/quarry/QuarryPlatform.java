package dev.cloudframe.common.quarry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.common.tubes.ItemPacketManager;

/**
 * Platform abstraction for quarry operations.
 * Implemented by Bukkit/Fabric layers to handle blocks, inventories, and effects.
 */
public interface QuarryPlatform {
    Object normalize(Object loc);
    Object offset(Object loc, int dx, int dy, int dz);
    boolean isChunkLoaded(Object loc);
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
    int blockX(Object loc);
    int blockY(Object loc);
    int blockZ(Object loc);
    int maxStackSize(Object itemStack);
    TubeNetworkManager tubes();
    ItemPacketManager packets();
    ItemPacketFactory packetFactory();

    interface ItemPacketFactory {
        void send(Object itemStack, List<Object> waypoints, Object destinationInventory, DeliveryCallback callback);
    }

    interface DeliveryCallback {
        void delivered(Object destination, int amount);
    }

    UUID ownerFromPlayer(Object player);
}
