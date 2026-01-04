package dev.cloudframe.common.util;

/**
 * Platform-agnostic debug flags for controlling log verbosity.
 */
public class DebugFlags {
    public static boolean TICK_LOGGING = false;

    // Visual debug only; keep off by default.
    public static boolean PIPE_PARTICLES = false;

    // Spammy on busy servers; keep off by default.
    public static boolean CHUNK_LOGGING = false;

    // Verbose pipe/quarry DB load/save logs; keep off by default.
    public static boolean STARTUP_LOAD_LOGGING = false;

    // Very verbose resourcepack build + HTTP request logs; keep off by default.
    public static boolean RESOURCE_PACK_VERBOSE_LOGGING = false;

    // Verbose entity-only visuals spawning logs; keep off by default.
    public static boolean VISUAL_SPAWN_LOGGING = false;

    // Pick-block diagnostics; enabled by default until stable.
    public static boolean PICKBLOCK_LOGGING = true;
}
