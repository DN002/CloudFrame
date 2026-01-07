package dev.cloudframe.common.pipes.filter;

import java.util.Arrays;

/**
 * Serialization for pipe filter slot lists.
 *
 * Format: 27 entries separated by '|'. Empty slot is stored as a single space.
 *
 * This matches the existing DB format used by Fabric so old saves remain compatible.
 */
public final class PipeFilterCodec {

    private PipeFilterCodec() {
    }

    public static String serializeItemIds(String[] itemIds) {
        if (itemIds == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PipeFilterState.SLOT_COUNT; i++) {
            if (i > 0) sb.append('|');
            String id = i < itemIds.length ? itemIds[i] : null;
            if (id == null || id.trim().isEmpty()) {
                sb.append(' ');
            } else {
                sb.append(id.trim());
            }
        }
        return sb.toString();
    }

    public static String[] deserializeItemIds(String raw) {
        String[] out = new String[PipeFilterState.SLOT_COUNT];
        Arrays.fill(out, null);

        if (raw == null || raw.isBlank()) return out;

        String[] parts = raw.split("\\|", -1);
        for (int i = 0; i < Math.min(PipeFilterState.SLOT_COUNT, parts.length); i++) {
            String p = parts[i];
            if (p == null) continue;
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.equals(" ")) continue;
            out[i] = p;
        }

        return out;
    }
}
