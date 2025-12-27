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
        posA.put(player, normalize(loc));
    }

    public void setPosB(UUID player, Location loc) {
        posB.put(player, normalize(loc));
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
