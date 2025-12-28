package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.Region;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class WrenchListener implements Listener {

    private static final Debug debug = DebugManager.get(WrenchListener.class);
    private static final int QUARRY_SIZE = 9;

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!isWrench(p.getInventory().getItemInMainHand())) {
            return;
        }

        if (e.getClickedBlock() == null) {
            debug.log("onInteract", "Player " + p.getName() + " used wrench but clicked no block");
            return;
        }

        Location clicked = e.getClickedBlock().getLocation();
        debug.log("onInteract", "Player " + p.getName() + " used Cloud Wrench at " + clicked);

        // Must have both marker positions
        if (!CloudFrameRegistry.markers().hasBoth(p.getUniqueId())) {
            debug.log("onInteract", "Player " + p.getName() + " missing marker positions");
            p.sendMessage("§cYou must set both marker positions first.");
            return;
        }

        // Normalize region
        Location a = CloudFrameRegistry.markers().getPosA(p.getUniqueId());
        Location b = CloudFrameRegistry.markers().getPosB(p.getUniqueId());
        Region region = new Region(a, b);

        debug.log("onInteract", "Raw region: " + region);

        // Expand region vertically: top = clicked Y, bottom = world min height
        int topY = clicked.getBlockY();
        int bottomY = region.getWorld().getMinHeight();

        region = new Region(
                new Location(region.getWorld(), region.minX(), bottomY, region.minZ()),
                new Location(region.getWorld(), region.maxX(), topY, region.maxZ())
        );

        debug.log("onInteract", "Expanded vertical region: " + region);

        // Validate size
        if (region.width() != QUARRY_SIZE || region.length() != QUARRY_SIZE) {
            debug.log("onInteract", "Invalid quarry size: width=" + region.width() +
                    " length=" + region.length());
            p.sendMessage("§cQuarry must be exactly " + QUARRY_SIZE + "x" + QUARRY_SIZE + ".");
            return;
        }

        // Check overlap with existing quarries
        for (Quarry q : CloudFrameRegistry.quarries().all()) {
            if (q.getRegion().intersects(region)) {
                debug.log("onInteract", "Quarry overlap detected with existing quarry owner=" + q.getOwner());
                p.sendMessage("§cThis quarry overlaps an existing quarry.");
                return;
            }
        }

        // Controller must be placed on the border of the region
        boolean onBorder =
                clicked.getBlockX() == region.minX() ||
                clicked.getBlockX() == region.maxX() ||
                clicked.getBlockZ() == region.minZ() ||
                clicked.getBlockZ() == region.maxZ();

        if (!onBorder) {
            debug.log("onInteract", "Clicked block not on border: " + clicked);
            p.sendMessage("§cController must be placed on the border of the quarry frame.");
            return;
        }

        int cx = clicked.getBlockX();
        int cy = region.maxY(); // controller always at top layer
        int cz = clicked.getBlockZ();

        if (cx == region.minX()) cx -= 1;
        else if (cx == region.maxX()) cx += 1;

        if (cz == region.minZ()) cz -= 1;
        else if (cz == region.maxZ()) cz += 1;

        Location controller = new Location(region.getWorld(), cx, cy, cz);

        // Ensure controller location is free
        if (!controller.getBlock().getType().isAir()) {
            debug.log("onInteract", "Controller location obstructed at " + controller);
            p.sendMessage("§cController location is obstructed.");
            return;
        }

        // Build border BEFORE placing controller
        debug.log("onInteract", "Building quarry border for region " + region);
        buildBorder(region);

        // Place controller block
        controller.getBlock().setType(Material.COPPER_BLOCK);
        debug.log("onInteract", "Placed controller block at " + controller);

        // Register quarry
        Quarry quarry = new Quarry(
                p.getUniqueId(),
                a,
                b,
                region,
                controller
        );

        CloudFrameRegistry.quarries().register(quarry);
        debug.log("onInteract", "Registered new quarry for owner=" + p.getUniqueId());

        p.sendMessage("§aQuarry frame created.");

        // Clear markers
        CloudFrameRegistry.markers().clear(p.getUniqueId());
        debug.log("onInteract", "Cleared markers for " + p.getName());
    }

    @SuppressWarnings("deprecation")
    private boolean isWrench(org.bukkit.inventory.ItemStack item) {
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;

        boolean isWrench = item.getItemMeta().getDisplayName().contains("Cloud Wrench");

        if (isWrench) {
            debug.log("isWrench", "Detected Cloud Wrench item");
        }

        return isWrench;
    }

    private void buildBorder(Region r) {
        var world = r.getWorld();
        int y = r.maxY(); // build the frame at the surface layer

        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {

                boolean border =
                        x == r.minX() || x == r.maxX() ||
                        z == r.minZ() || z == r.maxZ();

                if (border) {
                    world.getBlockAt(x, y, z).setType(Material.GLASS);
                }
            }
        }
        debug.log("buildBorder", "Border built at Y=" + y + " for region " + r);
    }
}
