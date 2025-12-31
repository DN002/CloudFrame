package dev.cloudframe.common.util;

/**
 * Platform-agnostic 3D region representation using block coordinates.
 * 
 * Stores world reference as Object to remain platform-neutral.
 * Bukkit: Object is org.bukkit.World
 * Fabric: Object is net.minecraft.world.World
 */
public class Region {

    private final Object world;

    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    /**
     * Create a region from two corner locations (platform-agnostic).
     * 
     * @param worldA World object (platform-specific)
     * @param ax Block X of corner A
     * @param ay Block Y of corner A
     * @param az Block Z of corner A
     * @param worldB World object (platform-specific)
     * @param bx Block X of corner B
     * @param by Block Y of corner B
     * @param bz Block Z of corner B
     */
    public Region(Object worldA, int ax, int ay, int az, Object worldB, int bx, int by, int bz) {
        if (!worldA.equals(worldB)) {
            throw new IllegalArgumentException("Region cannot span multiple worlds");
        }

        this.world = worldA;

        this.minX = Math.min(ax, bx);
        this.maxX = Math.max(ax, bx);

        this.minY = Math.min(ay, by);
        this.maxY = Math.max(ay, by);

        this.minZ = Math.min(az, bz);
        this.maxZ = Math.max(az, bz);
    }

    public Object getWorld() { return world; }

    public int minX() { return minX; }
    public int maxX() { return maxX; }

    public int minY() { return minY; }
    public int maxY() { return maxY; }

    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }

    public int width() { return maxX - minX + 1; }
    public int height() { return maxY - minY + 1; }
    public int length() { return maxZ - minZ + 1; }

    public int volume() {
        return width() * height() * length();
    }

    /**
     * Check if a location (block coordinates) is inside this region.
     * 
     * @param world World object (platform-specific)
     * @param x Block X
     * @param y Block Y
     * @param z Block Z
     * @return true if contained
     */
    public boolean contains(Object world, int x, int y, int z) {
        if (!this.world.equals(world)) return false;

        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Region other) {
        if (!this.world.equals(other.world)) return false;

        return this.maxX >= other.minX &&
               this.minX <= other.maxX &&
               this.maxY >= other.minY &&
               this.minY <= other.maxY &&
               this.maxZ >= other.minZ &&
               this.minZ <= other.maxZ;
    }

    @Override
    public String toString() {
        return "Region[" +
                minX + "," + minY + "," + minZ + " -> " +
                maxX + "," + maxY + "," + maxZ + "]";
    }
}
