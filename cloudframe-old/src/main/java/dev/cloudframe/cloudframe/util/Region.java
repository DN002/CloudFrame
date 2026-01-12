package dev.cloudframe.cloudframe.util;

import org.bukkit.Location;
import org.bukkit.World;

public class Region {

    private final World world;

    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public Region(Location a, Location b) {

        if (!a.getWorld().equals(b.getWorld())) {
            throw new IllegalArgumentException("Region cannot span multiple worlds");
        }

        this.world = a.getWorld();

        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());

        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());

        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    public World getWorld() { return world; }

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

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(world)) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

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
