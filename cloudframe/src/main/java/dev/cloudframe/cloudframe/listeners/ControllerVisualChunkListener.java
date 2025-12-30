package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import dev.cloudframe.cloudframe.quarry.QuarryManager;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ControllerVisualChunkListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerVisualChunkListener.class);

    private final QuarryManager quarryManager;

    public ControllerVisualChunkListener(QuarryManager quarryManager) {
        this.quarryManager = quarryManager;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();
        quarryManager.visualsManager().cleanupChunkEntities(chunk);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();

        quarryManager.visualsManager().cleanupChunkEntities(chunk);

        for (var loc : quarryManager.controllerLocationsInChunk(chunk)) {
            quarryManager.visualsManager().ensureController(loc, quarryManager.getControllerYaw(loc));
        }

        debug.log("chunkLoad", "Refreshed controller entities for chunk " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ());
    }
}
