package dev.cloudframe.cloudframe.markers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;

import dev.cloudframe.cloudframe.util.Region;

public class MarkerManager {

    private final Map<UUID, Location> posA = new HashMap<>();
    private final Map<UUID, Location> posB = new HashMap<>();

    public void setPosA(UUID player, Location loc) {
        loc = normalize(loc);
        posA.put(player, loc);
        System.out.println("[MarkerManager] Set marker A at " + loc + " for player " + player);
        tryCreateFrame(player);
    }

    public void setPosB(UUID player, Location loc) {
        loc = normalize(loc);
        posB.put(player, loc);
        System.out.println("[MarkerManager] Set marker B at " + loc + " for player " + player);
        tryCreateFrame(player);
    }
    
    /**
     * Automatically create and display glass frame when both markers are set.
     */
    private void tryCreateFrame(UUID player) {
        if (!hasBoth(player)) {
            System.out.println("[MarkerManager] Cannot create frame yet - missing markers for player " + player);
            return;
        }
        
        Location a = getPosA(player);
        Location b = getPosB(player);
        System.out.println("[MarkerManager] Both markers set! A=" + a + ", B=" + b);
        
        Region region = new Region(a, b);
        
        // Expand region vertically to world bottom
        int topY = Math.max(a.getBlockY(), b.getBlockY());
        int bottomY = a.getWorld().getMinHeight();
        
        region = new Region(
            new Location(a.getWorld(), region.minX(), bottomY, region.minZ()),
            new Location(a.getWorld(), region.maxX(), topY, region.maxZ())
        );
        
        System.out.println("[MarkerManager] Creating frame around region: " + region.minX() + "," + region.minY() + "," + region.minZ() + " to " + region.maxX() + "," + region.maxY() + "," + region.maxZ());
        
        // Place glass frame
        buildFrameAroundRegion(region);
    }
    
    /**
     * Build glass frame around region boundaries.
     */
    private void buildFrameAroundRegion(Region region) {
        org.bukkit.World world = region.getWorld();
        
        int minX = region.minX();
        int maxX = region.maxX();
        int minY = region.minY();
        int maxY = region.maxY();
        int minZ = region.minZ();
        int maxZ = region.maxZ();
        
        int blockCount = 0;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdgeX = (x == minX || x == maxX);
                    boolean isEdgeY = (y == minY || y == maxY);
                    boolean isEdgeZ = (z == minZ || z == maxZ);
                    
                    int edgeCount = (isEdgeX ? 1 : 0) + (isEdgeY ? 1 : 0) + (isEdgeZ ? 1 : 0);
                    if (edgeCount >= 2) {
                        Location loc = new Location(world, x, y, z);
                        if (loc.getBlock().getType().isAir()) {
                            loc.getBlock().setType(org.bukkit.Material.GLASS, false);
                            blockCount++;
                        }
                    }
                }
            }
        }
        
        System.out.println("[MarkerManager] Frame creation complete: placed " + blockCount + " glass blocks");
    }

    public Location getPosA(UUID player) {
        return posA.get(player);
    }

    public Location getPosB(UUID player) {
        return posB.get(player);
    }

    public boolean hasBoth(UUID player) {
        return posA.containsKey(player) && posB.containsKey(player);
    }

    public void clear(UUID player) {
        posA.remove(player);
        posB.remove(player);
    }

    // Optional convenience helper
    public Region getRegion(UUID player) {
        if (!hasBoth(player)) return null;
        return new Region(getPosA(player), getPosB(player));
    }

    private Location normalize(Location loc) {
        return loc.getBlock().getLocation();
    }
}
