package dev.cloudframe.common.platform.world;

/**
 * Platform adapter for converting world/dimension references to a stable key and back.
 *
 * <p>Bukkit: commonly world name (or NamespacedKey if available).
 * Fabric: commonly the dimension id (e.g. "minecraft:overworld").</p>
 */
public interface WorldKeyAdapter<WORLD> {
    String key(WORLD world);
    WORLD worldByKey(String key);
}
