package dev.cloudframe.common.pipes;

/**
 * Callback invoked when an item packet delivers items into an inventory.
 *
 * <p>The {@code itemStack} is the original stack object carried by the packet (platform-specific),
 * and {@code insertedAmount} is the number of items successfully inserted.</p>
 */
@FunctionalInterface
public interface ItemPacketDeliveryCallback {
    void delivered(Object destinationInventoryLocation, Object itemStack, int insertedAmount);
}
