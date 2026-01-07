package dev.cloudframe.common.platform.items;

/**
 * Portable inventory insertion algorithm.
 *
 * Mirrors the typical "merge into existing stacks, then fill empty slots" behavior.
 * Platforms supply adapters for stack equality/merge rules and slot IO.
 */
public final class InventoryInsert {

    private InventoryInsert() {
    }

    /**
     * Attempts to add the given {@code item} into {@code inventory}.
     *
     * The passed {@code item} is NOT mutated; adapters are expected to copy.
     *
     * @return number of items actually inserted (0..originalCount)
     */
    public static <INV, STACK> int addItem(
        INV inventory,
        STACK item,
        SlottedInventoryAdapter<INV, STACK> inv,
        ItemStackAdapter<STACK> stack
    ) {
        if (inventory == null || item == null || inv == null || stack == null) return 0;
        if (stack.isEmpty(item)) return 0;

        int original = stack.getCount(item);
        if (original <= 0) return 0;

        STACK remaining = stack.copy(item);

        // First pass: merge into compatible stacks.
        int size = inv.size(inventory);
        for (int i = 0; i < size && !stack.isEmpty(remaining); i++) {
            STACK slot = inv.getStack(inventory, i);
            if (slot == null || stack.isEmpty(slot)) continue;

            if (stack.canMerge(slot, remaining)) {
                int space = stack.getMaxCount(slot) - stack.getCount(slot);
                if (space > 0) {
                    int transfer = Math.min(space, stack.getCount(remaining));
                    stack.setCount(slot, stack.getCount(slot) + transfer);
                    stack.setCount(remaining, stack.getCount(remaining) - transfer);
                    inv.setStack(inventory, i, slot);
                }
            }
        }

        // Second pass: fill empty slots.
        for (int i = 0; i < size && !stack.isEmpty(remaining); i++) {
            STACK slot = inv.getStack(inventory, i);
            if (slot != null && !stack.isEmpty(slot)) continue;

            STACK placed = stack.copy(remaining);
            inv.setStack(inventory, i, placed);
            stack.setCount(remaining, 0);
        }

        inv.markDirty(inventory);

        int inserted = original - stack.getCount(remaining);
        return Math.max(0, inserted);
    }
}
