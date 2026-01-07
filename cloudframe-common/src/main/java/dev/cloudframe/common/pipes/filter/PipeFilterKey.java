package dev.cloudframe.common.pipes.filter;

import java.util.Objects;

/**
 * Platform-neutral identifier for a pipe-side filter.
 *
 * {@code worldId} should be a stable dimension identifier (e.g. "minecraft:overworld").
 */
public final class PipeFilterKey {

    private final String worldId;
    private final int x;
    private final int y;
    private final int z;
    private final int side;

    public PipeFilterKey(String worldId, int x, int y, int z, int side) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
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

    public int side() {
        return side;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PipeFilterKey that)) return false;
        return x == that.x && y == that.y && z == that.z && side == that.side && Objects.equals(worldId, that.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z, side);
    }
}
