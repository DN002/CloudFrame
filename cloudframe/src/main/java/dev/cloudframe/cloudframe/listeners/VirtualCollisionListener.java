package dev.cloudframe.cloudframe.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;

/**
 * Server-authoritative collision for entity-only tubes/controllers.
 *
 * The client-side BARRIER spoof can briefly drop during certain interactions;
 * this listener ensures players cannot fall into or move through virtual blockspaces
 * even if the client drops collision for a frame.
 */
public class VirtualCollisionListener implements Listener {

    private static final double EPS = 1.0E-4;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        Player p = e.getPlayer();
        if (p == null) return;

        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;

        // Ignore pure rotation.
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        World world = to.getWorld();

        BoundingBox bbNow = p.getBoundingBox();
        double widthX = bbNow.getWidthX();
        double widthZ = bbNow.getWidthZ();
        double height = bbNow.getHeight();

        BoundingBox fromBox = boxAt(from, widthX, widthZ, height);
        BoundingBox toBox = boxAt(to, widthX, widthZ, height);

        // Sticky virtual floor: if the player is standing on a virtual tube/controller blockspace,
        // never allow their feet to go below the top plane. This specifically fixes the
        // "right click => drop 1 block" correction when the server briefly stops treating them as supported.
        Integer supportYUnder = findSupportYUnderFoot(p, world, toBox);
        if (supportYUnder != null) {
            double topY = supportYUnder + 1.0;
            if (to.getY() < topY - 1.0E-3) {
                Location fixed = to.clone();
                fixed.setY(topY);
                e.setTo(fixed);
                p.setFallDistance(0.0f);
                return;
            }
        }

        // Scan blocks overlapped by the destination bounding box.
        int minX = (int) Math.floor(toBox.getMinX() + EPS);
        int maxX = (int) Math.floor(toBox.getMaxX() - EPS);
        int minY = (int) Math.floor(toBox.getMinY() + EPS);
        int maxY = (int) Math.floor(toBox.getMaxY() - EPS);
        int minZ = (int) Math.floor(toBox.getMinZ() + EPS);
        int maxZ = (int) Math.floor(toBox.getMaxZ() - EPS);

        boolean collided = false;
        int supportY = Integer.MIN_VALUE;
        boolean fallingOrStaying = to.getY() <= from.getY() + 1.0E-6;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isVirtualBlockspaceFor(p, world, x, y, z)) continue;
                    // Only apply virtual collision where the server block is air.
                    if (!world.getBlockAt(x, y, z).getType().isAir()) continue;

                    collided = true;

                    if (fallingOrStaying) {
                        // Support check: did we cross the top plane of this virtual block?
                        double topY = y + 1.0;
                        boolean wasAbove = fromBox.getMinY() >= topY - 0.05;
                        boolean nowAtOrBelow = toBox.getMinY() <= topY + 0.05;
                        if (wasAbove && nowAtOrBelow) {
                            supportY = Math.max(supportY, y);
                        }
                    }
                }
            }
        }

        if (!collided) return;

        // If collision is with support underfoot, snap to the top plane.
        if (supportY != Integer.MIN_VALUE) {
            double topY = supportY + 1.0;
            if (toBox.getMinY() < topY) {
                Location fixed = to.clone();
                fixed.setY(topY);
                e.setTo(fixed);
                p.setFallDistance(0.0f);
                return;
            }
        }

        // Otherwise, block the movement completely (full-block collision feel).
        e.setTo(from);
        p.setFallDistance(0.0f);
    }

    private static BoundingBox boxAt(Location feet, double widthX, double widthZ, double height) {
        double hx = widthX / 2.0;
        double hz = widthZ / 2.0;
        double minX = feet.getX() - hx;
        double maxX = feet.getX() + hx;
        double minZ = feet.getZ() - hz;
        double maxZ = feet.getZ() + hz;
        double minY = feet.getY();
        double maxY = feet.getY() + height;
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isVirtualBlockspaceFor(Player player, World world, int x, int y, int z) {
        if (world == null) return false;

        Location loc = new Location(world, x, y, z);

        // Prefer per-player spoof set (what the client is actually colliding with).
        if (player != null && ClientSelectionBoxTask.isSpoofedFor(player, loc)) {
            return true;
        }

        if (CloudFrameRegistry.tubes() != null && CloudFrameRegistry.tubes().getTube(loc) != null) {
            return true;
        }

        return CloudFrameRegistry.quarries() != null && CloudFrameRegistry.quarries().hasControllerAt(loc);
    }

    private static Integer findSupportYUnderFoot(Player player, World world, BoundingBox box) {
        if (player == null || world == null || box == null) return null;

        int minX = (int) Math.floor(box.getMinX() + EPS);
        int maxX = (int) Math.floor(box.getMaxX() - EPS);
        int minZ = (int) Math.floor(box.getMinZ() + EPS);
        int maxZ = (int) Math.floor(box.getMaxZ() - EPS);

        // Sample just below feet, but scan a small vertical window because the player can dip
        // during packet corrections.
        int baseY = (int) Math.floor(box.getMinY() - 0.01);

        int yTop = baseY + 2;
        int yBottom = baseY - 6;

        for (int y = yTop; y >= yBottom; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isVirtualBlockspaceFor(player, world, x, y, z)) continue;
                    if (!world.getBlockAt(x, y, z).getType().isAir()) continue;
                    return y;
                }
            }
        }

        return null;
    }
}
