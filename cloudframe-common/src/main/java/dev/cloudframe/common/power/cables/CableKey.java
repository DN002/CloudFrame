package dev.cloudframe.common.power.cables;

import java.util.Objects;

/**
 * Platform-neutral identifier for a cable block.
 *
 * {@code worldId} should be a stable dimension identifier (e.g. "minecraft:overworld").
 */
public final class CableKey {

    private final String worldId;
    private final int x;
    private final int y;
    private final int z;

    public CableKey(String worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CableKey that)) return false;
        return x == that.x && y == that.y && z == that.z && Objects.equals(worldId, that.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
