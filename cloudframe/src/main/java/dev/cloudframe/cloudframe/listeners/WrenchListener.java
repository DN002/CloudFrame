package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.Region;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class WrenchListener implements Listener {

    private static final Debug debug = DebugManager.get(WrenchListener.class);
    private static final int MIN_QUARRY_SIZE = 3;
    private static final int MAX_QUARRY_SIZE = 128;

    // Debounce finalization calls because controller clicks can be reported through multiple
    // interaction events (blockspace + entity) for the same user action.
    private static final java.util.Map<java.util.UUID, Long> lastFinalizeMs = new java.util.HashMap<>();
    private static final long FINALIZE_DEBOUNCE_MS = 150;

    private static boolean shouldDebounceFinalize(Player p) {
        if (p == null) return true;
        long now = System.currentTimeMillis();
        long last = lastFinalizeMs.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < FINALIZE_DEBOUNCE_MS) {
            return true;
        }
        lastFinalizeMs.put(p.getUniqueId(), now);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // Avoid double-fire (main hand + off hand).
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!isWrench(p.getInventory().getItemInMainHand())) {
            return;
        }

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // Finalization fallback: when the player clicks the controller's client-side selection box,
        // the server sees an "air" block click. Handle that first so we don't show marker errors.
        Location controllerLoc = null;
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock() != null && e.getClickedBlock().getType().isAir()) {
                Location maybeControllerLoc = e.getClickedBlock().getLocation();
                if (CloudFrameRegistry.quarries().hasControllerAt(maybeControllerLoc)) {
                    controllerLoc = maybeControllerLoc;
                }
            }
        } else if (action == Action.RIGHT_CLICK_AIR) {
            // Only do a look-based fallback for air clicks, so we don't hijack legitimate block clicks.
            controllerLoc = raytraceLookedController(p, false);
        }

        if (controllerLoc != null) {
            // If it's already finalized, don't do marker-frame logic either.
            if (CloudFrameRegistry.quarries().getByController(controllerLoc) != null) {
                e.setCancelled(true);
                return;
            }

            if (shouldDebounceFinalize(p)) {
                e.setCancelled(true);
                return;
            }

            debug.log("onInteract", "Finalizing quarry via controller blockspace at " + controllerLoc);
            finalizeQuarryAt(p, controllerLoc);
            e.setCancelled(true);
            return;
        }

        if (e.getClickedBlock() == null) {
            debug.log("onInteract", "Player " + p.getName() + " used wrench but clicked no block");
            return;
        }

        Location clicked = e.getClickedBlock().getLocation();
        debug.log("onInteract", "Player " + p.getName() + " used Cloud Wrench at " + clicked);

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
        if (!isValidQuarrySize(region)) {
            debug.log("onInteract", "Invalid quarry size: width=" + region.width() +
                    " length=" + region.length());
            p.sendMessage("§cQuarry must be between " + MIN_QUARRY_SIZE + ".." + MAX_QUARRY_SIZE + " blocks on each side (got " + region.width() + "x" + region.length() + ").");
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

        // If creating from markers (building the frame), do NOT auto-place a controller.
        // Controllers are entity-only now; the player places it separately.
        if (creatingFromMarkers) {
            debug.log("onInteract", "Building quarry border for region (markers) " + region + " — not placing controller");
            buildBorder(region);
            p.sendMessage("§aQuarry frame created. Place a Quarry Controller adjacent to the frame, then use the wrench on it to finalize.");
            CloudFrameRegistry.markers().clear(p.getUniqueId());
            debug.log("onInteract", "Cleared markers for " + p.getName());
            return;
        }

        if (clickedIsFrame) {
            p.sendMessage("§cPlace a Quarry Controller adjacent to the frame, then use the wrench to finalize.");
            return;
        }

        // Register quarry
        Quarry quarry = new Quarry(
                p.getUniqueId(),
                a,
                b,
                region,
            controller,
            0
        );

        CloudFrameRegistry.quarries().register(quarry);
        debug.log("onInteract", "Registered new quarry for owner=" + p.getUniqueId());

        p.sendMessage("§aQuarry frame created.");

        // Clear markers
        CloudFrameRegistry.markers().clear(p.getUniqueId());
        debug.log("onInteract", "Cleared markers for " + p.getName());
    }

    /**
     * Voxel ray-walk to find an unregistered controller blockspace the player is looking at.
     * This avoids relying on entity hitboxes (which are intentionally tiny).
     */
    private static Location raytraceLookedController(Player player, boolean requireUnregistered) {
        if (player == null) return null;
        var eye = player.getEyeLocation();
        var world = eye.getWorld();
        if (world == null) return null;

        Vector dir = eye.getDirection();
        if (dir == null) return null;
        dir = dir.clone();
        if (dir.lengthSquared() < 1.0E-9) return null;
        dir.normalize();

        // Keep it close to vanilla block reach.
        final double maxDist = 6.0;

        double ox = eye.getX();
        double oy = eye.getY();
        double oz = eye.getZ();

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        double dx = dir.getX();
        double dy = dir.getY();
        double dz = dir.getZ();

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double nextVoxelBoundaryX = stepX > 0 ? (x + 1.0) : x;
        double nextVoxelBoundaryY = stepY > 0 ? (y + 1.0) : y;
        double nextVoxelBoundaryZ = stepZ > 0 ? (z + 1.0) : z;

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryX - ox) / dx;
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryY - oy) / dy;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryZ - oz) / dz;

        // Ensure positive times.
        if (tMaxX < 0) tMaxX = 0;
        if (tMaxY < 0) tMaxY = 0;
        if (tMaxZ < 0) tMaxZ = 0;

        double traveled = 0.0;
        int steps = 0;
        int maxSteps = (int) Math.ceil(maxDist * 16);

        while (steps++ < maxSteps && traveled <= maxDist) {
            Location blockLoc = new Location(world, x, y, z);
            if (CloudFrameRegistry.quarries().hasControllerAt(blockLoc)) {
                if (!requireUnregistered || CloudFrameRegistry.quarries().getByController(blockLoc) == null) {
                    return blockLoc;
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    traveled = tMaxX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    traveled = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    traveled = tMaxY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    traveled = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return null;
    }

    @EventHandler
    public void onInteractControllerEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();

        // Avoid double-fire (main hand + off hand).
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!isWrench(p.getInventory().getItemInMainHand())) {
            return;
        }

        if (CloudFrameRegistry.quarries().visualsManager() == null) {
            return;
        }

        Location controllerLoc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(
                e.getRightClicked().getPersistentDataContainer()
        );
        if (controllerLoc == null) {
            return;
        }

        // Already registered? Let ControllerListener handle opening the GUI.
        if (CloudFrameRegistry.quarries().getByController(controllerLoc) != null) {
            return;
        }

        if (shouldDebounceFinalize(p)) {
            e.setCancelled(true);
            return;
        }

        debug.log("onInteract", "Finalizing quarry via controller entity at " + controllerLoc);
        finalizeQuarryAt(p, controllerLoc);
        e.setCancelled(true);
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
          * Returns a Region if a valid frame (MIN..MAX rectangle) is found, otherwise null.
     */
    private Region inferRegionFromBorder(Location controllerLoc) {
        var world = controllerLoc.getWorld();
        if (world == null) return null;

        int baseY = controllerLoc.getBlockY();

        // With entity-only controllers, players often place the controller one block higher/lower
        // than the glass frame plane. Search a small vertical band for a valid frame.
        int[] yCandidates = new int[] { baseY, baseY - 1, baseY + 1, baseY - 2, baseY + 2 };

        int range = MAX_QUARRY_SIZE + 10;
        int cx = controllerLoc.getBlockX();
        int cz = controllerLoc.getBlockZ();

        // 4-neighbor plane adjacency
        final int[][] DIRS_2D = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (int y : yCandidates) {
            // Controller must be adjacent to the glass frame on this Y plane.
            Location start = null;
            for (int[] d : DIRS_2D) {
                int sx = cx + d[0];
                int sz = cz + d[1];
                if (world.getBlockAt(sx, y, sz).getType() == Material.GLASS) {
                    start = new Location(world, sx, y, sz);
                    break;
                }
            }

            if (start == null) {
                debug.log("inferRegionFromBorder", "No adjacent glass at y=" + y + " for controller=" + controllerLoc);
                continue;
            }

            // Flood-fill only the connected glass component so other nearby glass doesn't break bounds.
            java.util.ArrayDeque<Location> queue = new java.util.ArrayDeque<>();
            java.util.HashSet<Long> visited = new java.util.HashSet<>();
            queue.add(start);

            Integer minX = null, maxX = null, minZ = null, maxZ = null;
            int glassCount = 0;

            while (!queue.isEmpty()) {
                Location cur = queue.removeFirst();
                int x = cur.getBlockX();
                int z = cur.getBlockZ();

                // Bound search to avoid runaway scans.
                if (Math.abs(x - cx) > range || Math.abs(z - cz) > range) {
                    continue;
                }

                long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                if (!visited.add(key)) continue;

                if (world.getBlockAt(x, y, z).getType() != Material.GLASS) continue;

                glassCount++;
                if (minX == null || x < minX) minX = x;
                if (maxX == null || x > maxX) maxX = x;
                if (minZ == null || z < minZ) minZ = z;
                if (maxZ == null || z > maxZ) maxZ = z;

                for (int[] d : DIRS_2D) {
                    queue.add(new Location(world, x + d[0], y, z + d[1]));
                }
            }

            if (minX == null || maxX == null || minZ == null || maxZ == null) {
                debug.log("inferRegionFromBorder", "Adjacent glass component empty at y=" + y + " controller=" + controllerLoc);
                continue;
            }

            int width = maxX - minX + 1;
            int length = maxZ - minZ + 1;

            debug.log(
                "inferRegionFromBorder",
                "Candidate connected frame at y=" + y + " bounds=" + minX + ".." + maxX + "," + minZ + ".." + maxZ +
                " size=" + width + "x" + length + " glassCount=" + glassCount
            );

            // The outer glass frame is (innerSize + 2) on each axis.
            // Accept any rectangle inner size in [MIN..MAX] per axis.
            int innerW = width - 2;
            int innerL = length - 2;
            if (innerW < MIN_QUARRY_SIZE || innerW > MAX_QUARRY_SIZE) continue;
            if (innerL < MIN_QUARRY_SIZE || innerL > MAX_QUARRY_SIZE) continue;

            // Validate it's a complete border ring (glass all along the perimeter).
            boolean borderOk = true;
            for (int x = minX; x <= maxX && borderOk; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (!border) continue;
                    if (world.getBlockAt(x, y, z).getType() != Material.GLASS) {
                        borderOk = false;
                        break;
                    }
                }
            }
            if (!borderOk) {
                debug.log("inferRegionFromBorder", "Frame perimeter check failed at y=" + y + " for bounds=" + minX + ".." + maxX + "," + minZ + ".." + maxZ);
                continue;
            }

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

        return null;
    }

    private void finalizeQuarryAt(Player p, Location controllerLoc) {
        debug.log("finalizeQuarryAt", "Finalizing quarry at placed controller " + controllerLoc);

        // Must have both marker positions
        Region region;
        if (!CloudFrameRegistry.markers().hasBoth(p.getUniqueId())) {
            debug.log("finalizeQuarryAt", "Player " + p.getName() + " missing marker positions — attempting to infer region from nearby frame");
            region = inferRegionFromBorder(controllerLoc);
            if (region == null) {
                debug.log("finalizeQuarryAt", "Could not infer region from border for controller " + controllerLoc);
                p.sendMessage("§cYou must set both marker positions first (or place the controller adjacent to a visible frame).");
                return;
            }
            debug.log("finalizeQuarryAt", "Inferred region from border: " + region);
        } else {
            // Get marker positions
            Location a = CloudFrameRegistry.markers().getPosA(p.getUniqueId());
            Location b = CloudFrameRegistry.markers().getPosB(p.getUniqueId());
            region = new Region(a, b);
        }

        debug.log("finalizeQuarryAt", "Raw region: " + region);

        // Expand region vertically: top = controller Y, bottom = world min height
        int topY = controllerLoc.getBlockY();
        int bottomY = region.getWorld().getMinHeight();

        region = new Region(
                new Location(region.getWorld(), region.minX(), bottomY, region.minZ()),
                new Location(region.getWorld(), region.maxX(), topY, region.maxZ())
        );

        debug.log("finalizeQuarryAt", "Expanded vertical region: " + region);

        // Validate size
        if (!isValidQuarrySize(region)) {
            debug.log("finalizeQuarryAt", "Invalid quarry size: width=" + region.width() +
                " length=" + region.length());
            p.sendMessage("§cQuarry must be between " + MIN_QUARRY_SIZE + ".." + MAX_QUARRY_SIZE + " blocks on each side (got " + region.width() + "x" + region.length() + ").");
            return;
        }

        // Check overlap with existing quarries
        for (Quarry q : CloudFrameRegistry.quarries().all()) {
            if (q.getRegion().intersects(region)) {
                debug.log("finalizeQuarryAt", "Quarry overlap detected with existing quarry owner=" + q.getOwner());
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

            debug.log("finalizeQuarryAt", "Controller adjacent to frame — accepting placement at " + controllerLoc);
        }

        // Do NOT generate the glass frame during finalization.
        // Require an existing valid frame near the controller (handles controller being one block above/below).
        Integer frameY = findExistingFrameY(controllerLoc, region);
        if (frameY == null) {
            debug.log("finalizeQuarryAt", "No valid glass frame found near controller=" + controllerLoc + " for region=" + region);
            p.sendMessage("§cNo valid glass frame found near this controller. Build the frame first, then finalize.");
            return;
        }

        // Prepare posA/posB for constructor (use region corners)
        Location aLoc = new Location(region.getWorld(), region.minX(), region.minY(), region.minZ());
        Location bLoc = new Location(region.getWorld(), region.maxX(), region.maxY(), region.maxZ());

        // Register quarry with the placed controller block as controller
        int controllerYaw = CloudFrameRegistry.quarries().getControllerYaw(controllerLoc);

        // Snapshot any stored augments on this (unregistered) controller BEFORE register() clears it.
        var stored = CloudFrameRegistry.quarries().getUnregisteredControllerData(controllerLoc);
        Quarry quarry = new Quarry(
            p.getUniqueId(),
            aLoc,
            bLoc,
            region,
            controllerLoc,
            controllerYaw
        );

        if (stored != null) {
            quarry.setSilkTouchAugment(stored.silkTouch());
            quarry.setSpeedAugmentLevel(stored.speedLevel());
        }

        CloudFrameRegistry.quarries().register(quarry);
        debug.log("finalizeQuarryAt", "Registered new quarry for owner=" + p.getUniqueId());

        p.sendMessage("§aQuarry controller finalized.");

        // Clear markers
        CloudFrameRegistry.markers().clear(p.getUniqueId());
        debug.log("finalizeQuarryAt", "Cleared markers for " + p.getName());

        // Ensure controller visuals exist for the now-registered quarry.
        if (CloudFrameRegistry.quarries().visualsManager() != null) {
            CloudFrameRegistry.quarries().visualsManager().ensureController(controllerLoc, controllerYaw);
        }
    }

    private static boolean isValidQuarrySize(Region region) {
        if (region == null) return false;

        int w = region.width();
        int l = region.length();

        if (w < MIN_QUARRY_SIZE || w > MAX_QUARRY_SIZE) return false;
        if (l < MIN_QUARRY_SIZE || l > MAX_QUARRY_SIZE) return false;
        return true;
    }

    private static Integer findExistingFrameY(Location controllerLoc, Region innerRegion) {
        if (controllerLoc == null || controllerLoc.getWorld() == null) return null;
        if (innerRegion == null || innerRegion.getWorld() == null) return null;

        int baseY = controllerLoc.getBlockY();
        int[] yCandidates = new int[] { baseY, baseY - 1, baseY + 1, baseY - 2, baseY + 2 };

        for (int y : yCandidates) {
            if (isGlassBorderPresent(innerRegion, y)) {
                return y;
            }
        }
        return null;
    }

    private static boolean isGlassBorderPresent(Region innerRegion, int frameY) {
        if (innerRegion == null || innerRegion.getWorld() == null) return false;

        int y = frameY;

        int minX = innerRegion.minX() - 1;
        int maxX = innerRegion.maxX() + 1;
        int minZ = innerRegion.minZ() - 1;
        int maxZ = innerRegion.maxZ() + 1;

        // Perimeter check only (O(perimeter)), not full fill.
        for (int x = minX; x <= maxX; x++) {
            if (innerRegion.getWorld().getBlockAt(x, y, minZ).getType() != Material.GLASS) return false;
            if (innerRegion.getWorld().getBlockAt(x, y, maxZ).getType() != Material.GLASS) return false;
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (innerRegion.getWorld().getBlockAt(minX, y, z).getType() != Material.GLASS) return false;
            if (innerRegion.getWorld().getBlockAt(maxX, y, z).getType() != Material.GLASS) return false;
        }

        return true;
    }
}
