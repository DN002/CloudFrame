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

        // Check if clicking a copper block (unregistered quarry controller)
        if (e.getClickedBlock().getType() == Material.COPPER_BLOCK) {
            // Check if this block is already a registered controller
            for (Quarry q : CloudFrameRegistry.quarries().all()) {
                if (q.getController().equals(clicked)) {
                    debug.log("onInteract", "Clicked block is already a registered controller");
                    return; // Already registered, let ControllerListener handle it
                }
            }

            // This is a placed controller block that needs to be registered
            finalizeQuarryWithBlock(p, clicked, e);
            return;
        }

        // Must have both marker positions for frame creation
        if (!CloudFrameRegistry.markers().hasBoth(p.getUniqueId())) {
            debug.log("onInteract", "Player " + p.getName() + " missing marker positions");
            p.sendMessage("§cYou must set both marker positions first.");
            return;
        }

        // Normalize region
        Location a = CloudFrameRegistry.markers().getPosA(p.getUniqueId());
        Location b = CloudFrameRegistry.markers().getPosB(p.getUniqueId());
        Region region = new Region(a, b);
        boolean creatingFromMarkers = true;

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

        // Determine if clicked block is part of the frame (glass)
        boolean clickedIsFrame = e.getClickedBlock().getType() == Material.GLASS;

        // Ensure controller location is free or already a copper block
        var existingType = controller.getBlock().getType();
        if (!existingType.isAir() && existingType != Material.COPPER_BLOCK) {
            debug.log("onInteract", "Controller location obstructed at " + controller);
            p.sendMessage("§cController location is obstructed.");
            return;
        }

        // If a controller block already exists at the computed controller location, reuse it
        if (existingType == Material.COPPER_BLOCK) {
            debug.log("onInteract", "Controller block already present at " + controller + " — reusing");
        } else {
            // If creating from markers (building the frame), do NOT auto-place the copper controller.
            if (creatingFromMarkers) {
                debug.log("onInteract", "Building quarry border for region (markers) " + region + " — not placing controller");
                buildBorder(region);
                p.sendMessage("§aQuarry frame created. Place a copper block adjacent to the frame, then use the wrench to finalize the controller.");
                // Clear markers since frame is created
                CloudFrameRegistry.markers().clear(p.getUniqueId());
                debug.log("onInteract", "Cleared markers for " + p.getName());
                return;
            }

            // If the player clicked on the glass frame, don't create a copper block out of thin air.
            if (clickedIsFrame) {
                debug.log("onInteract", "Clicked frame but no controller present; not auto-placing copper");
                p.sendMessage("§cPlace a controller block (copper) adjacent to the frame, then use the wrench to finalize.");
                return;
            }

            debug.log("onInteract", "Building quarry border for region " + region);
            buildBorder(region);

            // Place controller block
            controller.getBlock().setType(Material.COPPER_BLOCK);
            debug.log("onInteract", "Placed controller block at " + controller);
        }

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

        int minX = r.minX() - 1;
        int maxX = r.maxX() + 1;
        int minZ = r.minZ() - 1;
        int maxZ = r.maxZ() + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
                if (border) {
                    world.getBlockAt(x, y, z).setType(Material.GLASS);
                }
            }
        }
        debug.log("buildBorder", "Border built at Y=" + y + " for region " + r + " (frame placed one block outside)");
    }

    /**
     * Try to infer a quarry region by locating glass frame blocks near the controller.
     * Returns a Region if a valid frame (QUARRY_SIZE x QUARRY_SIZE) is found, otherwise null.
     */
    private Region inferRegionFromBorder(Location controllerLoc) {
        var world = controllerLoc.getWorld();
        int y = controllerLoc.getBlockY();

        int range = QUARRY_SIZE + 3;
        Integer minX = null, maxX = null, minZ = null, maxZ = null;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int x = controllerLoc.getBlockX() + dx;
                int z = controllerLoc.getBlockZ() + dz;
                if (x < controllerLoc.getBlockX() - range || x > controllerLoc.getBlockX() + range) continue;
                if (z < controllerLoc.getBlockZ() - range || z > controllerLoc.getBlockZ() + range) continue;

                if (world.getBlockAt(x, y, z).getType() == Material.GLASS) {
                    if (minX == null || x < minX) minX = x;
                    if (maxX == null || x > maxX) maxX = x;
                    if (minZ == null || z < minZ) minZ = z;
                    if (maxZ == null || z > maxZ) maxZ = z;
                }
            }
        }

        if (minX == null || maxX == null || minZ == null || maxZ == null) {
            return null;
        }

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;

        // We expect the glass frame to be two blocks larger than the inner quarry region
        if (width != QUARRY_SIZE + 2 || length != QUARRY_SIZE + 2) {
            debug.log("inferRegionFromBorder", "Found glass frame but dimensions not valid: " + width + "x" + length);
            return null;
        }

        // Inner region (one block inside the detected glass frame)
        int innerMinX = minX + 1;
        int innerMaxX = maxX - 1;
        int innerMinZ = minZ + 1;
        int innerMaxZ = maxZ - 1;

        int topY = y;
        int bottomY = world.getMinHeight();

        return new Region(
                new Location(world, innerMinX, bottomY, innerMinZ),
                new Location(world, innerMaxX, topY, innerMaxZ)
        );
    }

    private void finalizeQuarryWithBlock(Player p, Location controllerLoc, PlayerInteractEvent e) {
        debug.log("finalizeQuarryWithBlock", "Finalizing quarry at placed controller " + controllerLoc);

        // Must have both marker positions
        Region region;
        if (!CloudFrameRegistry.markers().hasBoth(p.getUniqueId())) {
            debug.log("finalizeQuarryWithBlock", "Player " + p.getName() + " missing marker positions — attempting to infer region from nearby frame");
            region = inferRegionFromBorder(controllerLoc);
            if (region == null) {
                debug.log("finalizeQuarryWithBlock", "Could not infer region from border for controller " + controllerLoc);
                p.sendMessage("§cYou must set both marker positions first (or place the controller adjacent to a visible frame).");
                return;
            }
            debug.log("finalizeQuarryWithBlock", "Inferred region from border: " + region);
        } else {
            // Get marker positions
            Location a = CloudFrameRegistry.markers().getPosA(p.getUniqueId());
            Location b = CloudFrameRegistry.markers().getPosB(p.getUniqueId());
            region = new Region(a, b);
        }

        debug.log("finalizeQuarryWithBlock", "Raw region: " + region);

        // Expand region vertically: top = controller Y, bottom = world min height
        int topY = controllerLoc.getBlockY();
        int bottomY = region.getWorld().getMinHeight();

        region = new Region(
                new Location(region.getWorld(), region.minX(), bottomY, region.minZ()),
                new Location(region.getWorld(), region.maxX(), topY, region.maxZ())
        );

        debug.log("finalizeQuarryWithBlock", "Expanded vertical region: " + region);

        // Validate size
        if (region.width() != QUARRY_SIZE || region.length() != QUARRY_SIZE) {
            debug.log("finalizeQuarryWithBlock", "Invalid quarry size: width=" + region.width() +
                    " length=" + region.length());
            p.sendMessage("§cQuarry must be exactly " + QUARRY_SIZE + "x" + QUARRY_SIZE + ".");
            return;
        }

        // Check overlap with existing quarries
        for (Quarry q : CloudFrameRegistry.quarries().all()) {
            if (q.getRegion().intersects(region)) {
                debug.log("finalizeQuarryWithBlock", "Quarry overlap detected with existing quarry owner=" + q.getOwner());
                p.sendMessage("§cThis quarry overlaps an existing quarry.");
                return;
            }
        }

        // Validate controller is on border (allow one-block offset outside inner region)
        int cx = controllerLoc.getBlockX();
        int cz = controllerLoc.getBlockZ();

        boolean onBorder =
            cx == region.minX() - 1 ||
            cx == region.maxX() + 1 ||
            cz == region.minZ() - 1 ||
            cz == region.maxZ() + 1 ||
            cx == region.minX() ||
            cx == region.maxX() ||
            cz == region.minZ() ||
            cz == region.maxZ();

        if (!onBorder) {
            // If region was inferred from a nearby frame, accept controller if it is adjacent to any frame glass block.
            boolean adjacentToFrame = false;
            var world = controllerLoc.getWorld();
            int y = controllerLoc.getBlockY();

            // Check the four horizontal neighbors for glass (frame) — if found, accept.
            int[][] checks = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] c : checks) {
                int nx = controllerLoc.getBlockX() + c[0];
                int nz = controllerLoc.getBlockZ() + c[1];
                if (world.getBlockAt(nx, y, nz).getType() == Material.GLASS) {
                    adjacentToFrame = true;
                    break;
                }
            }

            if (!adjacentToFrame) {
                debug.log("finalizeQuarryWithBlock", "Controller not on border: " + controllerLoc + " (expected on or one block outside border)");
                p.sendMessage("§cController must be placed on the border (or immediately outside) of the quarry frame.");
                return;
            }

            debug.log("finalizeQuarryWithBlock", "Controller adjacent to frame — accepting placement at " + controllerLoc);
        }

        // Build border (if not already present)
        debug.log("finalizeQuarryWithBlock", "Building quarry border for region " + region);
        buildBorder(region);

        // Prepare posA/posB for constructor (use region corners)
        Location aLoc = new Location(region.getWorld(), region.minX(), region.minY(), region.minZ());
        Location bLoc = new Location(region.getWorld(), region.maxX(), region.maxY(), region.maxZ());

        // Register quarry with the placed copper block as controller
        Quarry quarry = new Quarry(
            p.getUniqueId(),
            aLoc,
            bLoc,
            region,
            controllerLoc
        );

        CloudFrameRegistry.quarries().register(quarry);
        debug.log("finalizeQuarryWithBlock", "Registered new quarry for owner=" + p.getUniqueId());

        p.sendMessage("§aQuarry frame finalized.");

        // Clear markers
        CloudFrameRegistry.markers().clear(p.getUniqueId());
        debug.log("finalizeQuarryWithBlock", "Cleared markers for " + p.getName());

        e.setCancelled(true);
    }
}
