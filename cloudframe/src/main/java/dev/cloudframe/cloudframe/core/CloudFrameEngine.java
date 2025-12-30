package dev.cloudframe.cloudframe.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;

import dev.cloudframe.cloudframe.CloudFrame;
import dev.cloudframe.cloudframe.tubes.TubeNode;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugFlags;
import dev.cloudframe.cloudframe.util.DebugManager;

/**
 * Global engine for ticking, routing, and machine updates 
 */
public class CloudFrameEngine {

    private static final Debug debug = DebugManager.get(CloudFrameEngine.class);

    private int tickCounter = 0;
    private final CloudFrame plugin;
    private BukkitTask tickTask;

    public CloudFrameEngine(CloudFrame plugin) {
        this.plugin = plugin;
    }

    public void start() {
        debug.log("start", "Starting CloudFrame engine");
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        debug.log("stop", "Stopping CloudFrame engine");
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    private void tick() {
        tickCounter++;

        // Log only once per second (every 20 ticks)
        boolean shouldLog = DebugFlags.TICK_LOGGING && (tickCounter % 20 == 0);

        long start = System.nanoTime();
        if (shouldLog) {
            debug.log("tick", "Tick start");
        }

        try {
            // Tick quarries
            CloudFrameRegistry.quarries().tickAll(shouldLog);

            // Tick item packets
            CloudFrameRegistry.packets().tick(shouldLog);

            // Tube particle visualization (debug only)
            if (DebugFlags.TUBE_PARTICLES) {
                for (TubeNode node : CloudFrameRegistry.tubes().all()) {
                    Location loc = node.getLocation().clone().add(0.5, 0.5, 0.5);
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0);
                }
            }

        } catch (Exception ex) {
            debug.log("tick", "Exception during tick: " + ex.getMessage());
            ex.printStackTrace();
        }

        long end = System.nanoTime();
        long duration = (end - start) / 1_000_000; // ms

        if (shouldLog) {
            debug.log("tick", "Tick end (" + duration + " ms)");
        }

        // Lag detection always logs
        if (duration > 50) {
            debug.log("tick", "WARNING: Tick took " + duration + " ms (lag detected)");
        }
    }
}
