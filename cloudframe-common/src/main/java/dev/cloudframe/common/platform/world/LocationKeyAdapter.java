package dev.cloudframe.common.platform.world;

/**
 * Platform adapter for creating stable, comparable keys for block/world locations.
 *
 * <p>The key should uniquely identify the location across worlds/dimensions.
 * It should be stable across server restarts and independent of transient object identity.</p>
 */
public interface LocationKeyAdapter<LOC> {
    String key(LOC location);
}
