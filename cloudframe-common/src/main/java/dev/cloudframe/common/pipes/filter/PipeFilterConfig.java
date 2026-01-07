package dev.cloudframe.common.pipes.filter;

import java.util.Arrays;

/**
 * Portable, platform-neutral pipe filter configuration.
 *
 * Intended for sharing semantics between Fabric and future Bukkit:
 * - Mode is clamped to the supported values.
 * - Slot list is canonicalized (trim/blank->null, clear later duplicates).
 */
public final class PipeFilterConfig {

    private final int mode;
    private final String[] itemIds;

    public PipeFilterConfig(int mode, String[] itemIds) {
        this.mode = normalizeMode(mode);
        this.itemIds = PipeFilterCanonicalizer.canonicalizeSlotItemIds(itemIds);
    }

    public int mode() {
        return mode;
    }

    /**
     * Returns a canonicalized copy of the item ids (length = SLOT_COUNT).
     */
    public String[] copyItemIds() {
        return Arrays.copyOf(itemIds, PipeFilterState.SLOT_COUNT);
    }

    private static int normalizeMode(int mode) {
        return (mode == PipeFilterState.MODE_BLACKLIST) ? PipeFilterState.MODE_BLACKLIST : PipeFilterState.MODE_WHITELIST;
    }
}
