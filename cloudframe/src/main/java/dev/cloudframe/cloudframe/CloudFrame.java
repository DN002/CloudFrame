package dev.cloudframe.cloudframe;

import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;

import dev.cloudframe.cloudframe.commands.CloudFrameCommand;
import dev.cloudframe.cloudframe.core.CloudFrameEngine;
import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.listeners.ControllerGuiListener;
import dev.cloudframe.cloudframe.listeners.ControllerListener;
import dev.cloudframe.cloudframe.listeners.MarkerListener;
import dev.cloudframe.cloudframe.listeners.TubeListener;
import dev.cloudframe.cloudframe.listeners.WrenchListener;
import dev.cloudframe.cloudframe.storage.Database;
import dev.cloudframe.cloudframe.util.DebugFile;
import dev.cloudframe.cloudframe.util.DebugManager;

public class CloudFrame extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("[CloudFrame] Starting...");

        // Ensure plugin folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // initialize debug manager
        DebugManager.init(this);
        DebugFile.init();

        // Initialize SQLite
        try {
            Database.init(getDataFolder() + "/cloudframe.db");
            getLogger().info("[CloudFrame] SQLite initialized.");
        } catch (SQLException e) {
            getLogger().severe("[CloudFrame] Failed to initialize SQLite!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Create engine + managers
        CloudFrameEngine engine = new CloudFrameEngine(this);
        CloudFrameRegistry.init(engine);
        CloudFrameRegistry.init(this); // Set plugin reference for scheduler

        // Load saved data BEFORE ticking begins
        CloudFrameRegistry.tubes().loadAll();
        CloudFrameRegistry.quarries().loadAll();

        // Register listeners AFTER managers are ready
        getServer().getPluginManager().registerEvents(new MarkerListener(), this);
        getServer().getPluginManager().registerEvents(new TubeListener(), this);
        getServer().getPluginManager().registerEvents(new WrenchListener(), this);
        getServer().getPluginManager().registerEvents(new ControllerListener(), this);
        getServer().getPluginManager().registerEvents(new ControllerGuiListener(), this);

        // Start GUI update task
        ControllerGuiListener.startGuiUpdateTask();

        // Register commands safely
        if (getCommand("cloudframe") != null) {
            getCommand("cloudframe").setExecutor(new CloudFrameCommand());
        }

        // Start ticking engine last
        engine.start();

        getLogger().info("[CloudFrame] Ready.");
    }

    @Override
    public void onDisable() {
        // Stop engine safely
        if (CloudFrameRegistry.engine() != null) {
            CloudFrameRegistry.engine().stop();

            CloudFrameRegistry.quarries().saveAll();
            CloudFrameRegistry.tubes().saveAll();
        }

        // Stop GUI update task
        ControllerGuiListener.stopGuiUpdateTask();

        // Close SQLite connection
        Database.close();

        DebugManager.shutdown();
        getLogger().info("[CloudFrame] Stopped.");
    }
}
