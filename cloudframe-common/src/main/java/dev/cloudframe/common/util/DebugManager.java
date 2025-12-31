package dev.cloudframe.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Platform-agnostic debug manager.
 * 
 * Provides per-class Debug instances for structured logging.
 */
public class DebugManager {

    private static final Map<Class<?>, Debug> debuggers = new HashMap<>();

    /**
     * Get or create a Debug instance for the given class.
     */
    public static Debug get(Class<?> clazz) {
        return debuggers.computeIfAbsent(clazz, Debug::new);
    }

    /**
     * Shutdown all debug systems (flush buffers, close files).
     */
    public static void shutdown() {
        DebugFile.close();
    }
}
