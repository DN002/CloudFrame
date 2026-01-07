package dev.cloudframe.common.pipes;

/**
 * Minimal platform abstraction for querying pipe connectivity in-world.
 *
 * <p>This is intentionally small so both Fabric and Bukkit can implement it.
 * Common traversal logic (e.g. quarry output validation) should depend on this
 * interface instead of platform APIs.</p>
 */
public interface PipeConnectivityAccess {

    /**
     * Returns a stable, comparable representation of the location.
     *
     * <p>Implementations may return the input unchanged if already normalized.</p>
     */
    Object normalize(Object loc);

    /**
     * Returns whether the chunk containing this location is loaded.
     */
    boolean isChunkLoaded(Object loc);

    /**
     * Offsets the given location by a direction index.
     *
     * <p>Direction indices must match {@link PipeNetworkManager#DIRS}.</p>
     */
    Object offset(Object loc, int dirIndex);

    /**
     * Returns true if a pipe/tube block exists at this location.
     */
    boolean isPipeAt(Object loc);

    /**
     * Returns true if the pipe at {@code pipeLoc} has an enabled connection arm in {@code dirIndex}.
     *
     * <p>Direction indices must match {@link PipeNetworkManager#DIRS}.</p>
     */
    boolean pipeConnects(Object pipeLoc, int dirIndex);

    /**
     * Returns true if an inventory exists at this location.
     */
    boolean isInventoryAt(Object loc);
}
