package dev.cloudframe.common.platform.items;

/**
 * Shared, platform-agnostic inventory capacity helpers.
 *
 * <p>Uses the same merge rules as {@link InventoryInsert} (via adapters), but does not mutate
 * the inventory. This is useful for routing decisions like "does this destination have space?".</p>
 */
public final class InventoryCapacity {

    private InventoryCapacity() {
    }

    /**
     * Returns the maximum number of items from {@code incoming} that could be inserted into
     * {@code inventory} without mutating it.
     */
    public static <I, S> int maxInsertable(
            I inventory,
            S incoming,
            SlottedInventoryAdapter<I, S> inv,
            ItemStackAdapter<S> stacks
    ) {
        if (inventory == null || inv == null || stacks == null) return 0;
        if (incoming == null || stacks.isEmpty(incoming)) return 0;

        int remaining = stacks.getCount(incoming);
        if (remaining <= 0) return 0;

        int maxPerStack = stacks.getMaxCount(incoming);
        if (maxPerStack <= 0) return 0;

        int capacity = 0;

        int size = inv.size(inventory);
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            S slotStack = inv.getStack(inventory, slot);

            if (slotStack == null || stacks.isEmpty(slotStack)) {
                int room = maxPerStack;
                int add = Math.min(room, remaining);
                capacity += add;
                remaining -= add;
                continue;
            }

            if (!stacks.canMerge(slotStack, incoming)) {
                continue;
            }

            int room = Math.max(0, maxPerStack - stacks.getCount(slotStack));
            if (room <= 0) continue;

            int add = Math.min(room, remaining);
            capacity += add;
            remaining -= add;
        }

        return capacity;
    }

    /**
     * Returns the total room available for items that can merge with {@code prototype},
     * counting both empty slots (as full stacks) and partially-filled mergeable slots.
     *
     * <p>This is not limited by the current count of {@code prototype} and does not mutate
     * the inventory. It's useful for in-flight accounting, where multiple packets are already
     * en route to the same destination.</p>
     */
    public static <I, S> int totalRoomFor(
            I inventory,
            S prototype,
            SlottedInventoryAdapter<I, S> inv,
            ItemStackAdapter<S> stacks
    ) {
        if (inventory == null || inv == null || stacks == null) return 0;
        if (prototype == null || stacks.isEmpty(prototype)) return 0;

        int maxPerStack = stacks.getMaxCount(prototype);
        if (maxPerStack <= 0) return 0;

        int roomTotal = 0;
        int size = inv.size(inventory);

        for (int slot = 0; slot < size; slot++) {
            S slotStack = inv.getStack(inventory, slot);

            if (slotStack == null || stacks.isEmpty(slotStack)) {
                roomTotal += maxPerStack;
                continue;
            }

            if (!stacks.canMerge(slotStack, prototype)) {
                continue;
            }

            int room = Math.max(0, maxPerStack - stacks.getCount(slotStack));
            roomTotal += room;
        }

        return roomTotal;
    }

    public static <I, S> int emptySlotCount(
            I inventory,
            SlottedInventoryAdapter<I, S> inv,
            ItemStackAdapter<S> stacks
    ) {
        if (inventory == null || inv == null || stacks == null) return 0;

        int empty = 0;
        int size = inv.size(inventory);
        for (int slot = 0; slot < size; slot++) {
            S slotStack = inv.getStack(inventory, slot);
            if (slotStack == null || stacks.isEmpty(slotStack)) empty++;
        }
        return empty;
    }

    public static <I, S> boolean hasSpaceFor(
            I inventory,
            S incoming,
            SlottedInventoryAdapter<I, S> inv,
            ItemStackAdapter<S> stacks
    ) {
        if (incoming == null || stacks == null) return false;
        int needed = stacks.getCount(incoming);
        return maxInsertable(inventory, incoming, inv, stacks) >= needed;
    }
}
