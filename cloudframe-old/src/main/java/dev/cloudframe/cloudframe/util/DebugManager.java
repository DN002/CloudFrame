package dev.cloudframe.cloudframe.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.*;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit-specific debug manager initialization.
 * 
 * Wraps the common DebugManager and initializes DebugFile with the plugin folder.
 */
public class DebugManager {

    private static FileHandler handler;
    private static Logger logger;

    // Called from CloudFrame.onEnable()
    public static void init(JavaPlugin plugin) {
        // Initialize common debug file with plugin data folder path
        dev.cloudframe.common.util.DebugFile.init(plugin.getDataFolder().getAbsolutePath());

        try {
            File logFile = new File(plugin.getDataFolder(), "debug.log");

            // Ensure folder exists
            plugin.getDataFolder().mkdirs();

            // Clear old log on startup
            if (logFile.exists()) {
                logFile.delete();
            }

            // Create rotating handler: 5 MB max, 3 backups
            handler = new FileHandler(logFile.getAbsolutePath(), 5_000_000, 3, true);
            handler.setFormatter(new SimpleFormatter());

            logger = Logger.getLogger("CloudFrameDebug");
            logger.setUseParentHandlers(false); // don't spam console
            logger.addHandler(handler);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Called from CloudFrame.onDisable()
    public static void shutdown() {
        if (handler != null) {
            handler.flush();
            handler.close();
        }
        dev.cloudframe.common.util.DebugManager.shutdown();
    }

    /**
     * Get a Debug instance for the given class (delegates to common).
     */
    public static Debug get(Class<?> clazz) {
        return new Debug(clazz, logger);
    }
}
