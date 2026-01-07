package dev.cloudframe.common.pipes.filter;

import java.util.Arrays;

/**
 * Platform-neutral filter state.
 *
 * Stores item identifiers as strings (e.g. "minecraft:stone").
 */
public final class PipeFilterState {

    public static final int MODE_WHITELIST = 0;
    public static final int MODE_BLACKLIST = 1;

    public static final int SLOT_COUNT = 27;

    private int mode;
    private final String[] itemIds = new String[SLOT_COUNT];

    public PipeFilterState(int mode, String[] initialItemIds) {
        setMode(mode);
        Arrays.fill(this.itemIds, null);
        if (initialItemIds != null) {
            for (int i = 0; i < Math.min(SLOT_COUNT, initialItemIds.length); i++) {
                String id = normalizeId(initialItemIds[i]);
                if (id != null) {
                    this.itemIds[i] = id;
                }
            }
        }
    }

    public int mode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = (mode == MODE_BLACKLIST) ? MODE_BLACKLIST : MODE_WHITELIST;
    }

    public String getItemId(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;
        return itemIds[slot];
    }

    public void setItemId(int slot, String itemId) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        itemIds[slot] = normalizeId(itemId);
    }

    public String[] copyItemIds() {
        return Arrays.copyOf(itemIds, SLOT_COUNT);
    }

    public boolean allows(String itemId) {
        String normalized = normalizeId(itemId);
        if (normalized == null) return true;

        boolean emptyList = isEmptyList();
        boolean matched = containsItemId(normalized);

        // Policy: empty list = no filtering (allow everything), regardless of mode.
        if (emptyList) return true;

        if (mode == MODE_BLACKLIST) {
            // Blacklist: block only listed items.
            return !matched;
        }

        // Whitelist: allow only listed items.
        return matched;
    }

    private boolean isEmptyList() {
        for (String id : itemIds) {
            if (id != null) return false;
        }
        return true;
    }

    private boolean containsItemId(String id) {
        if (id == null) return false;
        for (String existing : itemIds) {
            if (id.equals(existing)) return true;
        }
        return false;
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        String s = id.trim();
        return s.isEmpty() ? null : s;
    }
}
