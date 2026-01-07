package dev.cloudframe.common.pipes.filter;

import java.util.Set;

/**
 * Platform-agnostic filter cache + operations.
 *
 * This service owns the in-memory cache of {@link PipeFilterState} keyed by {@link PipeFilterKey},
 * and uses {@link PipeFilterRepository} for persistence.
 */
public interface PipeFilterService {

    /** Loads all filters from persistence into memory. */
    void loadAll();

    /** Returns a view of keys currently loaded in memory. */
    Set<PipeFilterKey> keys();

    boolean hasFilter(PipeFilterKey key);

    PipeFilterState get(PipeFilterKey key);

    PipeFilterState getOrCreate(PipeFilterKey key);

    void setMode(PipeFilterKey key, int mode);

    void setItems(PipeFilterKey key, String[] itemIds);

    /**
     * Sets mode + items together as one logical update.
     *
     * Default implementation calls {@link #setMode(PipeFilterKey, int)} and {@link #setItems(PipeFilterKey, String[])}.
     * Implementations may override to persist in a single write.
     */
    default void setConfig(PipeFilterKey key, PipeFilterConfig config) {
        if (key == null || config == null) return;
        setMode(key, config.mode());
        setItems(key, config.copyItemIds());
    }

    void remove(PipeFilterKey key);

    void removeAllAt(String worldId, int x, int y, int z);

    boolean allows(PipeFilterKey key, String itemId);
}
