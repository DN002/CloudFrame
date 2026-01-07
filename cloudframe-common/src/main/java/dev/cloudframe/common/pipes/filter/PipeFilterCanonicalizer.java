package dev.cloudframe.common.pipes.filter;

import java.util.HashSet;
import java.util.Set;

/**
 * Canonicalization utilities for pipe filter slot lists.
 *
 * The goal is to keep persisted filter data deterministic across platforms/UIs without
 * changing the player-visible slot layout:
 * - Trims whitespace
 * - Converts blanks to {@code null}
 * - Removes duplicates by clearing later duplicates (keeps first occurrence)
 */
public final class PipeFilterCanonicalizer {

    private PipeFilterCanonicalizer() {
    }

    public static String[] canonicalizeSlotItemIds(String[] raw) {
        int slots = PipeFilterState.SLOT_COUNT;
        String[] out = new String[slots];

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < slots; i++) {
            String id = (raw != null && i < raw.length) ? raw[i] : null;
            id = normalize(id);
            if (id == null) {
                out[i] = null;
                continue;
            }

            if (seen.add(id)) {
                out[i] = id;
            } else {
                // Clear later duplicates to stabilize saved state without shifting items between slots.
                out[i] = null;
            }
        }

        return out;
    }

    private static String normalize(String id) {
        if (id == null) return null;
        String s = id.trim();
        return s.isEmpty() ? null : s;
    }
}
