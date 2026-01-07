package dev.cloudframe.common.power.cables;

import java.util.Set;

/**
 * Platform-agnostic cable connection state cache + operations.
 *
 * Owns an in-memory cache of {@link CableConnectionState} keyed by {@link CableKey},
 * and uses {@link CableConnectionRepository} for persistence.
 */
public interface CableConnectionService {

    /** Loads all persisted cable state into memory. */
    void loadAll();

    /** Returns a view of keys currently loaded in memory. */
    Set<CableKey> keys();

    int getDisabledSidesMask(CableKey key);

    boolean isSideDisabled(CableKey key, int dirIndex);

    void toggleSide(CableKey key, int dirIndex);

    void setSideDisabled(CableKey key, int dirIndex, boolean disabled);

    void setDisabledSidesMask(CableKey key, int mask);
}
