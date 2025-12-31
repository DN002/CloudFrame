package dev.cloudframe.bukkit.platform;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import dev.cloudframe.common.platform.EventSystem;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bukkit implementation of EventSystem.
 */
public class BukkitEventSystem implements EventSystem {
    private final JavaPlugin plugin;
    private final Logger logger;
    private long currentTick = 0;

    public BukkitEventSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> currentTick++, 1L, 1L);
    }

    @Override
    public void registerListener(Object listener) {
        if (listener instanceof Listener l) {
            Bukkit.getPluginManager().registerEvents(l, plugin);
        }
    }

    @Override
    public Object scheduleRepeatingTask(Consumer<Long> task, long initialDelayTicks, long intervalTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                task.accept(currentTick);
            } catch (Exception ex) {
                logger.warning("Exception in repeating task: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, initialDelayTicks, intervalTicks);
    }

    @Override
    public Object scheduleTask(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public long getCurrentTick() {
        return currentTick;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
