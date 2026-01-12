package dev.cloudframe.bukkit;

import java.nio.file.Path;
import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.cloudframe.bukkit.quarry.BukkitQuarryPlatform;
import dev.cloudframe.bukkit.pipes.BukkitItemDeliveryProvider;
import dev.cloudframe.bukkit.pipes.BukkitItemStackAdapter;
import dev.cloudframe.bukkit.pipes.BukkitSimplePipeVisuals;
import dev.cloudframe.bukkit.pipes.BukkitPacketVisuals;
import dev.cloudframe.bukkit.pipes.BukkitPipeLocationAdapter;
import dev.cloudframe.bukkit.pipes.BukkitPacketService;
import dev.cloudframe.bukkit.power.BukkitPowerCellRepository;
import dev.cloudframe.bukkit.power.BukkitPowerConfigAccess;
import dev.cloudframe.bukkit.power.BukkitPowerNetworkAdapter;
import dev.cloudframe.common.quarry.QuarryManager;
import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.pipes.ItemPacketManager;
import dev.cloudframe.common.pipes.PipeNetworkManager;
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
    private PipeNetworkManager pipeManager;
    private ItemPacketManager packetManager;
    private BukkitPacketVisuals packetVisuals;
    private BukkitItemStackAdapter itemStackAdapter;
    private BukkitTask packetTickTask;
    private BukkitPacketService packetService;
    private BukkitPipeLocationAdapter pipeLocationAdapter;
    private QuarryManager quarryManager;
    private BukkitTask quarryTickTask;
    private BukkitQuarryPlatform quarryPlatform;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

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

        // Instantiate pipe network manager with Bukkit adapters
        pipeLocationAdapter = new BukkitPipeLocationAdapter();
        pipeManager = new PipeNetworkManager(pipeLocationAdapter);
        pipeManager.setVisuals(new BukkitSimplePipeVisuals(this, pipeManager, pipeLocationAdapter));
        pipeManager.loadAll();

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
        quarryPlatform = new BukkitQuarryPlatform(pipeManager, packetManager, packetService);

        // Optional power prototype (vanilla Materials + SQLite-backed cell storage).
        boolean powerEnabled = getConfig().getBoolean("power.enabled", false);
        if (powerEnabled) {
            BukkitPowerCellRepository.ensureSchema();
            BukkitPowerConfigAccess powerAccess = BukkitPowerConfigAccess.fromConfig(getConfig().getConfigurationSection("power"), new BukkitPowerCellRepository());
            BukkitPowerNetworkAdapter powerAdapter = new BukkitPowerNetworkAdapter(powerAccess);
            quarryPlatform.setPowerAdapter(powerAdapter, true);
            getLogger().info("[CloudFrame] Power enabled (prototype mappings). Place cables/cells/producers per config.yml.");
        }

        quarryManager = new QuarryManager(quarryPlatform);
        quarryManager.loadAll();

        // Start quarry ticking (every 20 ticks = 1 second for verbose logging)
        quarryTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                boolean shouldLog = getServer().getCurrentTick() % 20 == 0;
                quarryPlatform.onTickStart(getServer().getCurrentTick());
                quarryManager.tickAll(shouldLog);
                quarryPlatform.onTickEnd();
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

        if (pipeManager != null) {
            pipeManager.saveAll();
            if (pipeManager.visualsManager() != null) {
                pipeManager.visualsManager().shutdown();
            }
        }

        if (packetTickTask != null) {
            packetTickTask.cancel();
        }

        quarryManager = null;
        pipeManager = null;
        packetManager = null;
        packetVisuals = null;
        itemStackAdapter = null;
        packetService = null;
        pipeLocationAdapter = null;

        Database.close();
        
        // Shutdown debug system
        DebugManager.shutdown();

        // TODO: Cleanup resources
        // - Stop engine
        // - Save pending data
        // - Clean up entities
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

    public PipeNetworkManager getPipeManager() {
        return pipeManager;
    }
}
