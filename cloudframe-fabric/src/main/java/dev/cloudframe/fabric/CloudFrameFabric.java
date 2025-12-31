package dev.cloudframe.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.cloudframe.common.util.DebugFile;
import dev.cloudframe.common.util.DebugManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.tubes.ItemPacketManager;
import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.common.quarry.QuarryManager;
import dev.cloudframe.fabric.quarry.FabricQuarryPlatform;
import dev.cloudframe.fabric.tubes.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * CloudFrame Fabric Mod - Multi-platform version
 * 
 * Initializes managers and platform adapters for the Fabric platform.
 */
public class CloudFrameFabric implements ModInitializer {

    public static final String MOD_ID = "cloudframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Debug debug;

    private TubeNetworkManager tubeManager;
    private ItemPacketManager packetManager;
    private QuarryManager quarryManager;
    private MinecraftServer server;

    /**
     * Called when the mod initializes.
     * Set up by Fabric's entry point system (see fabric.mod.json).
     */
    @Override
    public void onInitialize() {
        LOGGER.info("[CloudFrame] Fabric mod initializing...");

        // Initialize debug system - creates debug.log in config folder
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("cloudframe");
        configDir.toFile().mkdirs();
        DebugFile.init(configDir.toString());
        debug = DebugManager.get(CloudFrameFabric.class);

        debug.log("onInitialize", "CloudFrame Fabric 2.0.0 initializing");
        LOGGER.info("[CloudFrame] Debug logging initialized to: " + configDir);

        // Initialize SQLite database
        try {
            Database.init(configDir.resolve("cloudframe.db").toString());
            debug.log("onInitialize", "SQLite database initialized");
            LOGGER.info("[CloudFrame] SQLite initialized");
        } catch (SQLException e) {
            debug.log("onInitialize", "FATAL: Failed to initialize SQLite: " + e.getMessage());
            LOGGER.error("[CloudFrame] Failed to initialize SQLite!", e);
            return;
        }

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Register tick event for packet/quarry updates
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        debug.log("onInitialize", "Lifecycle events registered");
        LOGGER.info("[CloudFrame] Fabric mod ready.");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        debug.log("onServerStarted", "Server started, initializing managers...");
        LOGGER.info("[CloudFrame] Server started, initializing managers...");

        var overworld = server.getOverworld();

        tubeManager = new TubeNetworkManager(new FabricTubeLocationAdapter(server));
        tubeManager.setVisuals(new FabricSimpleTubeVisuals(overworld));
        debug.log("onServerStarted", "TubeNetworkManager initialized");

        packetManager = new ItemPacketManager(new FabricItemDeliveryProvider(overworld));
        debug.log("onServerStarted", "ItemPacketManager initialized");

        FabricQuarryPlatform quarryPlatform = new FabricQuarryPlatform(overworld, tubeManager, packetManager);
        quarryManager = new QuarryManager(quarryPlatform);
        debug.log("onServerStarted", "QuarryManager initialized");

        tubeManager.loadAll();
        debug.log("onServerStarted", "Tubes loaded from database");
        
        quarryManager.loadAll();
        debug.log("onServerStarted", "Quarries loaded from database");

        LOGGER.info("[CloudFrame] Managers initialized and data loaded.");
    }

    private void onServerStopping(MinecraftServer server) {
        debug.log("onServerStopping", "Server stopping, saving data...");
        LOGGER.info("[CloudFrame] Server stopping, saving data...");

        if (tubeManager != null) {
            tubeManager.saveAll();
            debug.log("onServerStopping", "Tubes saved");
        }
        if (quarryManager != null) {
            quarryManager.saveAll();
            debug.log("onServerStopping", "Quarries saved");
        }

        Database.close();
        debug.log("onServerStopping", "Database closed");

        LOGGER.info("[CloudFrame] Data saved and database closed.");
    }

    private void onServerTick(MinecraftServer server) {
        if (packetManager != null) {
            try {
                packetManager.tick(false);
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking packets: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        if (quarryManager != null) {
            try {
                quarryManager.tickAll(false);
            } catch (Exception ex) {
                debug.log("onServerTick", "Exception ticking quarries: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Called when the mod shuts down (if implemented).
     */
    public void onShutdown() {
        if (debug != null) {
            debug.log("onShutdown", "CloudFrame Fabric 2.0.0 shutting down");
        }
        LOGGER.info("[CloudFrame] Fabric mod disabled.");
        DebugManager.shutdown();
    }

    // Public accessors for managers (for event handlers, commands, etc.)
    public TubeNetworkManager getTubeManager() {
        return tubeManager;
    }

    public ItemPacketManager getPacketManager() {
        return packetManager;
    }

    public QuarryManager getQuarryManager() {
        return quarryManager;
    }
}

