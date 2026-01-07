package dev.cloudframe.common.pipes.filter;

import dev.cloudframe.common.platform.items.ItemIdRegistry;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Shared conversion helpers for mapping platform stacks to pipe-filter item ids and back.
 *
 * This keeps Fabric and Bukkit using the same normalization/canonicalization rules while
 * allowing each platform to supply its own stack/item types.
 */
public final class PipeFilterStackMapper {

    private PipeFilterStackMapper() {
    }

    public static <STACK, ITEM> String[] itemIdsFromStacks(
            STACK[] stacks,
            Function<STACK, Boolean> isEmpty,
            Function<STACK, ITEM> itemOfStack,
            ItemIdRegistry<ITEM> ids
    ) {
        String[] raw = new String[PipeFilterState.SLOT_COUNT];
        for (int i = 0; i < raw.length; i++) {
            STACK s = (stacks != null && i < stacks.length) ? stacks[i] : null;
            if (s == null || Boolean.TRUE.equals(isEmpty.apply(s))) {
                raw[i] = null;
                continue;
            }

            ITEM item = itemOfStack.apply(s);
            raw[i] = ids == null ? null : ids.idOf(item);
        }

        // Canonicalize so persistence is deterministic cross-platform.
        return PipeFilterCanonicalizer.canonicalizeSlotItemIds(raw);
    }

    public static <STACK, ITEM> STACK[] stacksFromItemIds(
            String[] itemIds,
            IntFunction<STACK[]> arrayFactory,
            Supplier<STACK> emptyStack,
            Function<ITEM, STACK> createStack,
            ItemIdRegistry<ITEM> ids
    ) {
        STACK[] out = arrayFactory.apply(PipeFilterState.SLOT_COUNT);
        fillStacksFromItemIds(itemIds, out, emptyStack, createStack, ids);
        return out;
    }

    public static <STACK, ITEM> void fillStacksFromItemIds(
            String[] itemIds,
            STACK[] out,
            Supplier<STACK> emptyStack,
            Function<ITEM, STACK> createStack,
            ItemIdRegistry<ITEM> ids
    ) {
        if (out == null) return;
        for (int i = 0; i < Math.min(PipeFilterState.SLOT_COUNT, out.length); i++) {
            out[i] = emptyStack.get();
        }

        if (ids == null) return;

        String[] canonical = PipeFilterCanonicalizer.canonicalizeSlotItemIds(itemIds);
        for (int i = 0; i < Math.min(PipeFilterState.SLOT_COUNT, out.length); i++) {
            String id = canonical[i];
            if (id == null) continue;
            ITEM item = ids.itemById(id);
            if (item == null) continue;
            out[i] = createStack.apply(item);
        }
    }
}
