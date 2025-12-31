package dev.cloudframe.bukkit;

import java.nio.file.Path;
import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.cloudframe.bukkit.quarry.BukkitQuarryPlatform;
import dev.cloudframe.bukkit.tubes.BukkitItemDeliveryProvider;
import dev.cloudframe.bukkit.tubes.BukkitItemStackAdapter;
import dev.cloudframe.bukkit.tubes.BukkitSimpleTubeVisuals;
import dev.cloudframe.bukkit.tubes.BukkitPacketVisuals;
import dev.cloudframe.bukkit.tubes.BukkitTubeLocationAdapter;
import dev.cloudframe.bukkit.tubes.BukkitPacketService;
import dev.cloudframe.common.quarry.QuarryManager;
import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.tubes.ItemPacketManager;
import dev.cloudframe.common.tubes.TubeNetworkManager;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFile;
import dev.cloudframe.common.util.DebugManager;

/**
 * CloudFrame Bukkit Plugin - Multi-platform version
 * 
 * Initializes debug logging and platform adapters for the Bukkit platform.
 */
public class CloudFrameBukkit extends JavaPlugin {

    private static Debug debug;
    private TubeNetworkManager tubeManager;
    private ItemPacketManager packetManager;
    private BukkitPacketVisuals packetVisuals;
    private BukkitItemStackAdapter itemStackAdapter;
    private BukkitTask packetTickTask;
    private BukkitPacketService packetService;
    private BukkitTubeLocationAdapter tubeLocationAdapter;
    private QuarryManager quarryManager;
    private BukkitTask quarryTickTask;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize debug system - creates debug.log in plugin folder
        DebugFile.init(getDataFolder().getAbsolutePath());
        debug = DebugManager.get(CloudFrameBukkit.class);

        // Initialize SQLite once for all managers
        Path dbPath = getDataFolder().toPath().resolve("cloudframe.db");
        try {
            Database.init(dbPath.toString());
            getLogger().info("[CloudFrame] SQLite initialized at " + dbPath);
        } catch (SQLException e) {
            getLogger().severe("[CloudFrame] Failed to initialize SQLite: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Instantiate tube network manager with Bukkit adapters
        tubeLocationAdapter = new BukkitTubeLocationAdapter();
        tubeManager = new TubeNetworkManager(tubeLocationAdapter);
        tubeManager.setVisuals(new BukkitSimpleTubeVisuals(this, tubeManager, tubeLocationAdapter));
        tubeManager.loadAll();

        // Item packet routing (visuals + delivery)
        packetVisuals = new BukkitPacketVisuals();
        itemStackAdapter = new BukkitItemStackAdapter();
        packetManager = new ItemPacketManager(new BukkitItemDeliveryProvider());
        packetService = new BukkitPacketService(packetManager, packetVisuals, itemStackAdapter);

        // Start packet ticking
        packetTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                packetManager.tick(false);
            } catch (Exception ex) {
                getLogger().warning("Packet tick error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 1L, 1L);

        // Quarry manager with platform adapter
        BukkitQuarryPlatform quarryPlatform = new BukkitQuarryPlatform(tubeManager, packetManager, packetService);
        quarryManager = new QuarryManager(quarryPlatform);
        quarryManager.loadAll();

        // Start quarry ticking (every 20 ticks = 1 second for verbose logging)
        quarryTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                boolean shouldLog = getServer().getCurrentTick() % 20 == 0;
                quarryManager.tickAll(shouldLog);
            } catch (Exception ex) {
                getLogger().warning("Quarry tick error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 1L, 1L);

        getLogger().info("[CloudFrame] Bukkit plugin loaded successfully!");
        debug.log("onEnable", "CloudFrame Bukkit 2.0.0 enabled - debug.log available in plugin folder");

        // TODO: Initialize platform adapters and start CloudFrame engine
        // - Create BukkitEventSystem, BukkitBlockAccessor, BukkitEntityRenderer
        // - Initialize CloudFrameEngine
        // - Register event listeners
        // - Start tick loop
    }

    @Override
    public void onDisable() {
        if (debug != null) {
            debug.log("onDisable", "CloudFrame Bukkit 2.0.0 disabled");
        }
        getLogger().info("[CloudFrame] Bukkit plugin disabled.");

        if (quarryTickTask != null) {
            quarryTickTask.cancel();
        }

        if (quarryManager != null) {
            quarryManager.saveAll();
        }

        if (tubeManager != null) {
            tubeManager.saveAll();
            if (tubeManager.visualsManager() != null) {
                tubeManager.visualsManager().shutdown();
            }
        }

        if (packetTickTask != null) {
            packetTickTask.cancel();
        }

        quarryManager = null;
        packetManager = null;
        packetVisuals = null;
        itemStackAdapter = null;
        packetService = null;

        Database.close();
        
        // Shutdown debug system
        DebugManager.shutdown();

        // TODO: Cleanup resources
        // - Stop engine
        // - Save pending data
        // - Clean up entities
    }

    public TubeNetworkManager getTubeManager() {
        return tubeManager;
    }

    public ItemPacketManager getPacketManager() {
        return packetManager;
    }

    public BukkitPacketService getPacketService() {
        return packetService;
    }

    public QuarryManager getQuarryManager() {
        return quarryManager;
    }
}
