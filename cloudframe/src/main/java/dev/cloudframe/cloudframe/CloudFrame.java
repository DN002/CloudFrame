package dev.cloudframe.cloudframe;

import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;

import dev.cloudframe.cloudframe.commands.CloudFrameCommand;
import dev.cloudframe.cloudframe.core.CloudFrameEngine;
import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.listeners.ControllerGuiListener;
import dev.cloudframe.cloudframe.listeners.ControllerListener;
import dev.cloudframe.cloudframe.listeners.HoverHighlightTask;
import dev.cloudframe.cloudframe.listeners.ItemPacketListener;
import dev.cloudframe.cloudframe.listeners.ProtocolHoverOutlineTask;
import dev.cloudframe.cloudframe.listeners.MarkerListener;
import dev.cloudframe.cloudframe.listeners.TubeListener;
import dev.cloudframe.cloudframe.listeners.WrenchListener;
import dev.cloudframe.cloudframe.listeners.InventoryTubeRefreshListener;
import dev.cloudframe.cloudframe.listeners.ClientSelectionBoxTask;
import dev.cloudframe.cloudframe.listeners.OccupiedBlockSpaceListener;
import dev.cloudframe.cloudframe.storage.Database;
import dev.cloudframe.cloudframe.util.DebugFile;
import dev.cloudframe.cloudframe.util.DebugManager;
import dev.cloudframe.cloudframe.util.RecipeManager;
import dev.cloudframe.cloudframe.listeners.ResourcePackHandler;

public class CloudFrame extends JavaPlugin {

    private ResourcePackHandler resourcePackHandler;

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

        // Init tube visuals (BlockDisplays) after plugin reference is available
        CloudFrameRegistry.tubes().initVisuals(this);

        // Init controller visuals (entity-only)
        CloudFrameRegistry.quarries().initVisuals(this);

        // Load saved data BEFORE ticking begins
        CloudFrameRegistry.tubes().loadAll();
        CloudFrameRegistry.quarries().loadAll();

        // Register listeners AFTER managers are ready
        getServer().getPluginManager().registerEvents(new MarkerListener(), this);
        getServer().getPluginManager().registerEvents(new TubeListener(), this);
        getServer().getPluginManager().registerEvents(new WrenchListener(), this);
        getServer().getPluginManager().registerEvents(new ControllerListener(), this);
        getServer().getPluginManager().registerEvents(new ControllerGuiListener(), this);
        getServer().getPluginManager().registerEvents(new ItemPacketListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryTubeRefreshListener(), this);
        getServer().getPluginManager().registerEvents(new OccupiedBlockSpaceListener(), this);

        // Start GUI update task
        ControllerGuiListener.startGuiUpdateTask();

        // Start hover highlight task.
        // Vanilla-style selection box for entity-only blocks (tubes/controllers).
        // This avoids particles/glow and matches the normal Minecraft block outline.
        ClientSelectionBoxTask.start(this);

        // Register crafting recipes for plugin items
        RecipeManager.register(this);

        // Initialize embedded resource pack handler (Option B)
        resourcePackHandler = new ResourcePackHandler(this);
        resourcePackHandler.init();

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

            CloudFrameRegistry.tubes().shutdownVisuals();

            CloudFrameRegistry.quarries().shutdownVisuals();

            CloudFrameRegistry.quarries().saveAll();
            CloudFrameRegistry.tubes().saveAll();
        }

        // Shutdown resource pack handler
        if (resourcePackHandler != null) {
            resourcePackHandler.shutdown();
        }
        // Stop GUI update task
        ControllerGuiListener.stopGuiUpdateTask();

        ClientSelectionBoxTask.stop();

        // Close SQLite connection
        Database.close();

        DebugManager.shutdown();
        getLogger().info("[CloudFrame] Stopped.");
    }
}
