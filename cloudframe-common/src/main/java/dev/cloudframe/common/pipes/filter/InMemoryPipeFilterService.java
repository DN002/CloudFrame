package dev.cloudframe.common.pipes.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default portable implementation: an in-memory cache backed by SQLite persistence.
 */
public final class InMemoryPipeFilterService implements PipeFilterService {

    private final Map<PipeFilterKey, PipeFilterState> filters = new HashMap<>();

    @Override
    public void loadAll() {
        filters.clear();
        filters.putAll(PipeFilterRepository.loadAll());
    }

    @Override
    public Set<PipeFilterKey> keys() {
        return Collections.unmodifiableSet(new HashSet<>(filters.keySet()));
    }

    @Override
    public boolean hasFilter(PipeFilterKey key) {
        return key != null && filters.containsKey(key);
    }

    @Override
    public PipeFilterState get(PipeFilterKey key) {
        return key == null ? null : filters.get(key);
    }

    @Override
    public PipeFilterState getOrCreate(PipeFilterKey key) {
        if (key == null) return null;
        PipeFilterState existing = filters.get(key);
        if (existing != null) return existing;

        PipeFilterState created = new PipeFilterState(PipeFilterState.MODE_WHITELIST, null);
        filters.put(key, created);
        PipeFilterRepository.upsert(key, created);
        return created;
    }

    @Override
    public void setMode(PipeFilterKey key, int mode) {
        PipeFilterState st = getOrCreate(key);
        if (st == null) return;
        st.setMode(mode);
        PipeFilterRepository.upsert(key, st);
    }

    @Override
    public void setItems(PipeFilterKey key, String[] itemIds) {
        PipeFilterState st = getOrCreate(key);
        if (st == null) return;

        if (itemIds == null) itemIds = new String[0];
        for (int i = 0; i < PipeFilterState.SLOT_COUNT; i++) {
            String id = i < itemIds.length ? itemIds[i] : null;
            st.setItemId(i, id);
        }

        PipeFilterRepository.upsert(key, st);
    }

    @Override
    public void remove(PipeFilterKey key) {
        if (key == null) return;
        filters.remove(key);
        PipeFilterRepository.delete(key);
    }

    @Override
    public void removeAllAt(String worldId, int x, int y, int z) {
        // Remove from memory
        Set<PipeFilterKey> toRemove = new HashSet<>();
        for (PipeFilterKey k : filters.keySet()) {
            if (k == null) continue;
            if (x == k.x() && y == k.y() && z == k.z()) {
                String w = k.worldId();
                if (worldId == null ? w == null : worldId.equals(w)) {
                    toRemove.add(k);
                }
            }
        }
        for (PipeFilterKey k : toRemove) {
            filters.remove(k);
        }

        PipeFilterRepository.deleteAllAt(worldId, x, y, z);
    }

    @Override
    public boolean allows(PipeFilterKey key, String itemId) {
        PipeFilterState st = get(key);
        if (st == null) return true;
        return st.allows(itemId);
    }
}
