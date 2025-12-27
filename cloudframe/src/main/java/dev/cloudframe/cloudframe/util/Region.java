package dev.cloudframe.cloudframe.util;

import org.bukkit.Location;
import org.bukkit.World;

public class Region {

    private final Location a;
    private final Location b;

    public Region(Location a, Location b) {
        this.a = a;
        this.b = b;
    }

    public World getWorld() {
        return a.getWorld();
    }

    public int minX() { return Math.min(a.getBlockX(), b.getBlockX()); }
    public int maxX() { return Math.max(a.getBlockX(), b.getBlockX()); }

    public int minY() { return Math.min(a.getBlockY(), b.getBlockY()); }
    public int maxY() { return Math.max(a.getBlockY(), b.getBlockY()); }

    public int minZ() { return Math.min(a.getBlockZ(), b.getBlockZ()); }
    public int maxZ() { return Math.max(a.getBlockZ(), b.getBlockZ()); }

    public int width() { return maxX() - minX() + 1; }
    public int height() { return maxY() - minY() + 1; }
    public int length() { return maxZ() - minZ() + 1; }

    public int volume() {
        return width() * height() * length();
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(getWorld())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX() && x <= maxX()
            && y >= minY() && y <= maxY()
            && z >= minZ() && z <= maxZ();
    }

    public boolean intersects(Region other) {
        if (!this.getWorld().equals(other.getWorld())) return false;

        return this.maxX() >= other.minX() &&
               this.minX() <= other.maxX() &&
               this.maxY() >= other.minY() &&
               this.minY() <= other.maxY() &&
               this.maxZ() >= other.minZ() &&
               this.minZ() <= other.maxZ();
    }

    @Override
    public String toString() {
        return "Region[" +
            minX() + "," + minY() + "," + minZ() + " -> " +
            maxX() + "," + maxY() + "," + maxZ() + "]";
    }
}
