package dev.cloudframe.fabric.config;

import java.nio.file.Path;

/**
 * Deprecated: debug flags are configured via config/cloudframe/config.txt.
 *
 * This class remains only for binary/source compatibility with older jars.
 */
@Deprecated(forRemoval = true)
public final class DebugFlagsConfig {

    private DebugFlagsConfig() {
    }

    /**
     * No-op. Use {@link CloudFrameConfigFile} instead.
     */
    public static DebugFlagsConfig loadOrCreate(Path file) {
        return new DebugFlagsConfig();
    }
}
