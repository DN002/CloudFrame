package dev.cloudframe.common.pipes.connections;

import java.util.Set;

/**
 * Platform-agnostic pipe connection state cache + operations.
 *
 * Owns an in-memory cache of {@link PipeConnectionState} keyed by {@link PipeKey},
 * and uses {@link PipeConnectionRepository} for persistence.
 */
public interface PipeConnectionService {

    /** Loads all persisted pipe connection state into memory (only non-zero masks). */
    void loadAll();

    /** Returns a view of keys currently loaded in memory. */
    Set<PipeKey> keys();

    int getDisabledSidesMask(PipeKey key);

    boolean isSideDisabled(PipeKey key, int dirIndex);

    void toggleSide(PipeKey key, int dirIndex);

    void setSideDisabled(PipeKey key, int dirIndex, boolean disabled);

    void setDisabledSidesMask(PipeKey key, int mask);
}
