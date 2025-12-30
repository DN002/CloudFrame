package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import dev.cloudframe.cloudframe.tubes.TubeNetworkManager;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class TubeVisualChunkListener implements Listener {

    private static final Debug debug = DebugManager.get(TubeVisualChunkListener.class);

    private final TubeNetworkManager tubeManager;

    public TubeVisualChunkListener(TubeNetworkManager tubeManager) {
        this.tubeManager = tubeManager;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();
        tubeManager.visualsManager().cleanupChunkDisplays(chunk);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();

        // Remove any stale displays (in case they were persisted)
        tubeManager.visualsManager().cleanupChunkDisplays(chunk);

        // Recreate visuals for tubes in this chunk
        for (var loc : tubeManager.tubeLocationsInChunk(chunk)) {
            tubeManager.visualsManager().updateTube(loc);
        }

        debug.log("chunkLoad", "Refreshed tube displays for chunk " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ());
    }
}
