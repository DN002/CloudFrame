package dev.cloudframe.common.power.cables;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default portable implementation: an in-memory cache backed by SQLite persistence.
 */
public final class InMemoryCableConnectionService implements CableConnectionService {

    private final Map<CableKey, CableConnectionState> states = new HashMap<>();

    @Override
    public void loadAll() {
        states.clear();
        states.putAll(CableConnectionRepository.loadAll());
    }

    @Override
    public Set<CableKey> keys() {
        return Collections.unmodifiableSet(new HashSet<>(states.keySet()));
    }

    @Override
    public int getDisabledSidesMask(CableKey key) {
        if (key == null) return 0;
        CableConnectionState st = states.get(key);
        return st == null ? 0 : st.disabledSidesMask();
    }

    @Override
    public boolean isSideDisabled(CableKey key, int dirIndex) {
        if (key == null) return false;
        CableConnectionState st = states.get(key);
        if (st == null) return false;
        return st.isSideDisabled(dirIndex);
    }

    @Override
    public void toggleSide(CableKey key, int dirIndex) {
        if (key == null) return;
        CableConnectionState st = states.get(key);
        if (st == null) st = new CableConnectionState(0);

        st.toggleSide(dirIndex);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            CableConnectionRepository.delete(key);
        } else {
            states.put(key, st);
            CableConnectionRepository.upsert(key, st);
        }
    }

    @Override
    public void setSideDisabled(CableKey key, int dirIndex, boolean disabled) {
        if (key == null) return;
        CableConnectionState st = states.get(key);
        if (st == null) st = new CableConnectionState(0);

        st.setSideDisabled(dirIndex, disabled);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            CableConnectionRepository.delete(key);
        } else {
            states.put(key, st);
            CableConnectionRepository.upsert(key, st);
        }
    }

    @Override
    public void setDisabledSidesMask(CableKey key, int mask) {
        if (key == null) return;
        CableConnectionState st = new CableConnectionState(mask);

        if (st.disabledSidesMask() == 0) {
            states.remove(key);
            CableConnectionRepository.delete(key);
        } else {
            states.put(key, st);
            CableConnectionRepository.upsert(key, st);
        }
    }
}
