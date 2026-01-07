package dev.cloudframe.common.pipes.connections;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default portable implementation: an in-memory cache backed by SQLite persistence.
 */
public final class InMemoryPipeConnectionService implements PipeConnectionService {

    private final Map<PipeKey, PipeConnectionState> states = new HashMap<>();

    @Override
    public void loadAll() {
        states.clear();
        states.putAll(PipeConnectionRepository.loadAllNonZero());
    }

    @Override
    public Set<PipeKey> keys() {
        return Collections.unmodifiableSet(new HashSet<>(states.keySet()));
    }

    @Override
    public int getDisabledSidesMask(PipeKey key) {
        if (key == null) return 0;
        PipeConnectionState st = states.get(key);
        return st == null ? 0 : st.disabledSidesMask();
    }

    @Override
    public boolean isSideDisabled(PipeKey key, int dirIndex) {
        if (key == null) return false;
        PipeConnectionState st = states.get(key);
        if (st == null) return false;
        return st.isSideDisabled(dirIndex);
    }

    @Override
    public void toggleSide(PipeKey key, int dirIndex) {
        if (key == null) return;
        PipeConnectionState st = states.get(key);
        if (st == null) st = new PipeConnectionState(0);

        st.toggleSide(dirIndex);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            PipeConnectionRepository.upsertMask(key, 0);
        } else {
            states.put(key, st);
            PipeConnectionRepository.upsertMask(key, st.disabledSidesMask());
        }
    }

    @Override
    public void setSideDisabled(PipeKey key, int dirIndex, boolean disabled) {
        if (key == null) return;
        PipeConnectionState st = states.get(key);
        if (st == null) st = new PipeConnectionState(0);

        st.setSideDisabled(dirIndex, disabled);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            PipeConnectionRepository.upsertMask(key, 0);
        } else {
            states.put(key, st);
            PipeConnectionRepository.upsertMask(key, st.disabledSidesMask());
        }
    }

    @Override
    public void setDisabledSidesMask(PipeKey key, int mask) {
        if (key == null) return;
        PipeConnectionState st = new PipeConnectionState(mask);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            PipeConnectionRepository.upsertMask(key, 0);
        } else {
            states.put(key, st);
            PipeConnectionRepository.upsertMask(key, st.disabledSidesMask());
        }
    }
}
