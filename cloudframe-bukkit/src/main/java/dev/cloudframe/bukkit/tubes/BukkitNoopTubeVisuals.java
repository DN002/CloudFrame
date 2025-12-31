package dev.cloudframe.bukkit.tubes;

import dev.cloudframe.common.tubes.TubeNetworkManager;

/**
 * Placeholder visuals implementation; updates are no-ops until full visuals are ported.
 */
public class BukkitNoopTubeVisuals implements TubeNetworkManager.ITubeVisuals {
    @Override
    public void updateTubeAndNeighbors(Object loc) {
        // visuals not implemented yet
    }

    @Override
    public void shutdown() {
        // visuals not implemented yet
    }
}
