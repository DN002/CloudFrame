package dev.cloudframe.common.platform.world;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.WarnOnce;

/**
 * Shared policy for handling "dimension-less" locations.
 *
 * <p>Some platforms (or legacy call sites) may pass a location object that has
 * coordinates but no world/dimension. This is unsafe for persistence and routing.
 * Platform layers can use this helper to consistently warn and document that they
 * are falling back to a default world/dimension.</p>
 */
public final class LocationNormalizationPolicy {

    private LocationNormalizationPolicy() {
    }

    public static void warnAssumingDefaultWorld(
            AtomicBoolean gate,
            Debug debug,
            String context,
            String locationType,
            String defaultWorldKey
    ) {
        String type = (locationType == null || locationType.isBlank()) ? "location" : locationType;
        String world = (defaultWorldKey == null || defaultWorldKey.isBlank()) ? "default" : defaultWorldKey;

        WarnOnce.warn(
                gate,
                debug,
                context,
                "WARNING: Received a dimension-less " + type + "; assuming '" + world + "'. " +
                        "This can cause cross-dimension misrouting. Prefer dimension-aware locations everywhere."
        );
    }
}
