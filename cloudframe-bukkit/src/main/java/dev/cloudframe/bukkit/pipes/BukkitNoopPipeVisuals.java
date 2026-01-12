package dev.cloudframe.bukkit.pipes;

import dev.cloudframe.common.pipes.PipeNetworkManager;

/** No-op visuals implementation. */
public class BukkitNoopPipeVisuals implements PipeNetworkManager.IPipeVisuals {
    @Override
    public void updatePipeAndNeighbors(Object loc) {
    }

    @Override
    public void shutdown() {
    }
}
