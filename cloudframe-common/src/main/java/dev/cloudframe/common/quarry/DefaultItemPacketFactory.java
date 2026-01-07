package dev.cloudframe.common.quarry;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import dev.cloudframe.common.pipes.ItemPacket;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.platform.items.ItemStackAdapter;

/**
 * Shared {@link QuarryPlatform.ItemPacketFactory} implementation.
 *
 * <p>Platforms provide the visuals implementation and the item stack adapter.
 * This keeps packet creation behavior 1:1 across Fabric/Bukkit while leaving
 * only the rendering/entity details platform-specific.</p>
 */
public final class DefaultItemPacketFactory implements QuarryPlatform.ItemPacketFactory {

    private final ItemPacketManager packetManager;
    private final Supplier<ItemPacket.IPacketVisuals> visualsSupplier;
    private final ItemStackAdapter<?> stackAdapter;

    public DefaultItemPacketFactory(
            ItemPacketManager packetManager,
            Supplier<ItemPacket.IPacketVisuals> visualsSupplier,
            ItemStackAdapter<?> stackAdapter
    ) {
        this.packetManager = Objects.requireNonNull(packetManager, "packetManager");
        this.visualsSupplier = Objects.requireNonNull(visualsSupplier, "visualsSupplier");
        this.stackAdapter = Objects.requireNonNull(stackAdapter, "stackAdapter");
    }

    @Override
    public void send(Object itemStack, List<Object> waypoints, Object destinationInventory, QuarryPlatform.DeliveryCallback callback) {
        if (itemStack == null || waypoints == null || waypoints.isEmpty()) return;

        ItemPacket.IPacketVisuals visuals;
        try {
            visuals = visualsSupplier.get();
        } catch (Throwable ignored) {
            return;
        }
        if (visuals == null) return;

        try {
            ItemPacket packet = new ItemPacket(
                    itemStack,
                    waypoints,
                    destinationInventory,
                    callback,
                    visuals,
                    stackAdapter
            );
            packetManager.add(packet);
        } catch (Throwable ignored) {
            // If a platform provides the wrong stack type or adapter, fail closed (no packet).
        }
    }
}
