package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Interaction;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.CustomBlocks;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class TubeListener implements Listener {

    private static final Debug debug = DebugManager.get(TubeListener.class);

    private static final Vector[] DIRS = {
        new Vector(1, 0, 0),
        new Vector(-1, 0, 0),
        new Vector(0, 1, 0),
        new Vector(0, -1, 0),
        new Vector(0, 0, 1),
        new Vector(0, 0, -1)
    };

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceOnTube(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!CustomBlocks.isTubeItem(e.getPlayer().getInventory().getItemInMainHand())) return;

        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        var tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(
            e.getRightClicked().getPersistentDataContainer()
        );
        if (tubeLoc == null) return;

        // Prevent other entity interactions (Interaction has no vanilla behavior, but this
        // also avoids plugins double-handling right clicks).
        e.setCancelled(true);

        // Place on the clicked face (like block placement).
        Location targetLoc = computeTargetFromEntityClick(e.getPlayer(), e.getRightClicked(), tubeLoc);
        if (targetLoc == null) return;

        // Prevent placing a tube into a controller location.
        if (CloudFrameRegistry.quarries().hasControllerAt(targetLoc)) {
            e.getPlayer().sendMessage("§cYou can't place a tube inside a Quarry Controller.");
            return;
        }

        if (!targetLoc.getChunk().isLoaded()) return;
        if (!targetLoc.getBlock().getType().isAir()) return;
        if (CloudFrameRegistry.tubes().getTube(targetLoc) != null) return;

        debug.log("onPlaceOnTube", "Player " + e.getPlayer().getName() + " placed tube at " + targetLoc + " (clicked " + tubeLoc + ")");

        CloudFrameRegistry.tubes().addTube(targetLoc);
        if (CloudFrameRegistry.tubes().visualsManager() != null) {
            CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(targetLoc);
        }

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            var handItem = e.getPlayer().getInventory().getItemInMainHand();
            int amt = handItem.getAmount();
            if (amt <= 1) {
                e.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(amt - 1);
                e.getPlayer().getInventory().setItemInMainHand(handItem);
            }
        }

        pulseTube(targetLoc);
        e.getPlayer().sendMessage("§bTube placed.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceInventoryOnTube(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) return;

        // Tube placement is handled elsewhere.
        if (CustomBlocks.isTubeItem(handItem)) return;

        Material held = handItem.getType();
        // Only handle inventory blocks (so this doesn't become a weird "place any block via tube click" mechanic).
        if (held != Material.CHEST && held != Material.TRAPPED_CHEST && held != Material.BARREL) return;

        var tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(
            e.getRightClicked().getPersistentDataContainer()
        );
        if (tubeLoc == null) return;

        Location targetLoc = computeTargetFromEntityClick(e.getPlayer(), e.getRightClicked(), tubeLoc);
        if (targetLoc == null) return;

        if (!targetLoc.getChunk().isLoaded()) return;
        Block target = targetLoc.getBlock();
        if (!target.getType().isAir()) return;

        // Don't allow placing an inventory block "inside" a controller.
        if (CloudFrameRegistry.quarries().hasControllerAt(targetLoc)) {
            e.getPlayer().sendMessage("§cYou can't place a chest inside a Quarry Controller.");
            return;
        }

        // This click was on an entity, so vanilla won't place the block for us.
        e.setCancelled(true);

        target.setType(held, true);

        // Best-effort facing like vanilla placement.
        BlockData data = target.getBlockData();
        if (data instanceof Directional directional) {
            BlockFace facing = e.getPlayer().getFacing().getOppositeFace();
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                target.setBlockData(directional, true);
            }
        }

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            int amt = handItem.getAmount();
            if (amt <= 1) {
                e.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(amt - 1);
                e.getPlayer().getInventory().setItemInMainHand(handItem);
            }
        }

        // Refresh any adjacent tubes so connections appear immediately.
        if (CloudFrameRegistry.tubes().visualsManager() != null) {
            for (Vector v : DIRS) {
                Location adj = targetLoc.clone().add(v);
                if (CloudFrameRegistry.tubes().getTube(adj) != null) {
                    CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(adj);
                }
            }
        }
    }

    private static Location computeTargetFromEntityClick(org.bukkit.entity.Player player, org.bukkit.entity.Entity clicked, Location baseTubeLoc) {
        if (player == null || clicked == null || baseTubeLoc == null || baseTubeLoc.getWorld() == null) return null;

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult rr = player.getWorld().rayTraceEntities(
            eye,
            dir,
            6.0,
            0.2,
            e -> e.getUniqueId().equals(clicked.getUniqueId())
        );

        Vector hit = rr != null ? rr.getHitPosition() : null;
        return computeAdjacentFromHit(baseTubeLoc, hit, dir);
    }

    private static Location computeAdjacentFromHit(Location baseBlockLoc, Vector hitPosition, Vector lookDir) {
        if (baseBlockLoc == null || baseBlockLoc.getWorld() == null) return null;

        if (hitPosition == null) {
            return computeAdjacentFromLook(baseBlockLoc, lookDir);
        }

        double cx = baseBlockLoc.getBlockX() + 0.5;
        double cy = baseBlockLoc.getBlockY() + 0.5;
        double cz = baseBlockLoc.getBlockZ() + 0.5;

        double dx = hitPosition.getX() - cx;
        double dy = hitPosition.getY() - cy;
        double dz = hitPosition.getZ() - cz;

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        // If click is near center, use look direction.
        if (ax < 0.2 && ay < 0.2 && az < 0.2) {
            return computeAdjacentFromLook(baseBlockLoc, lookDir);
        }

        if (ax >= ay && ax >= az) {
            return baseBlockLoc.clone().add(dx >= 0 ? 1 : -1, 0, 0);
        }
        if (ay >= ax && ay >= az) {
            return baseBlockLoc.clone().add(0, dy >= 0 ? 1 : -1, 0);
        }
        return baseBlockLoc.clone().add(0, 0, dz >= 0 ? 1 : -1);
    }

    private static Location computeAdjacentFromLook(Location baseBlockLoc, Vector lookDir) {
        if (baseBlockLoc == null || lookDir == null) return null;

        double ax = Math.abs(lookDir.getX());
        double ay = Math.abs(lookDir.getY());
        double az = Math.abs(lookDir.getZ());

        if (ay >= ax && ay >= az) {
            return baseBlockLoc.clone().add(0, lookDir.getY() >= 0 ? 1 : -1, 0);
        }
        if (ax >= ay && ax >= az) {
            return baseBlockLoc.clone().add(lookDir.getX() >= 0 ? 1 : -1, 0, 0);
        }
        return baseBlockLoc.clone().add(0, 0, lookDir.getZ() >= 0 ? 1 : -1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!CustomBlocks.isTubeItem(e.getItem())) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Block target = clicked.getRelative(e.getBlockFace());
        if (!target.getType().isAir()) {
            return;
        }

        // Prevent normal copper block placement (we handle tube placement ourselves).
        e.setCancelled(true);

        Location loc = target.getLocation();

        // Prevent placing a tube into a controller location.
        if (CloudFrameRegistry.quarries().hasControllerAt(loc)) {
            e.getPlayer().sendMessage("§cYou can't place a tube inside a Quarry Controller.");
            return;
        }

        if (CloudFrameRegistry.tubes().getTube(loc) != null) {
            return;
        }

        debug.log("onPlace", "Player " + e.getPlayer().getName() + " placed tube at " + loc);

        // Defensive: ensure visuals are initialized even if startup init order changed.
        if (CloudFrameRegistry.tubes().visualsManager() == null && CloudFrameRegistry.plugin() != null) {
            debug.log("onPlace", "Tube visuals were not initialized; initializing now");
            CloudFrameRegistry.tubes().initVisuals(CloudFrameRegistry.plugin());
        }

        CloudFrameRegistry.tubes().addTube(loc);

        // Force a refresh so players see the tube immediately.
        if (CloudFrameRegistry.tubes().visualsManager() != null) {
            try {
                CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(loc);
            } catch (Exception ex) {
                debug.log("onPlace", "Exception forcing tube visuals refresh at " + loc + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            // This should never happen if initVisuals succeeded.
            debug.log("onPlace", "Tube visuals manager is still null after init; tube will be particles-only");
        }

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            var handItem = e.getPlayer().getInventory().getItemInMainHand();
            int amt = handItem.getAmount();
            if (amt <= 1) {
                e.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(amt - 1);
                e.getPlayer().getInventory().setItemInMainHand(handItem);
            }
        }

        pulseTube(loc);
        e.getPlayer().sendMessage("§bTube placed.");
    }

    /**
     * When using the client-side selection box, the client clicks a fake block at the tube
     * coordinate. Server-side that block is air, so vanilla placement won't work.
     *
     * This handler makes tubes behave like a normal placement anchor: right-clicking the tube
     * outline with a regular block item places that block into the adjacent blockspace.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceNormalBlockAgainstTube(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        final Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) return;

        // Let our tube-placement handler handle tube items.
        if (CustomBlocks.isTubeItem(handItem)) return;

        // Don't interfere with controller item placement.
        if (CustomBlocks.isControllerItem(handItem)) return;

        Material held = handItem.getType();
        if (!held.isBlock()) return;

        // Let the dedicated inventory placement logic handle these.
        if (held == Material.CHEST || held == Material.TRAPPED_CHEST || held == Material.BARREL) return;

        // Avoid half-baked multi-block placement (doors, tall flowers, beds, etc.).
        // These should be placed against a real vanilla block so vanilla can handle the second part.
        try {
            BlockData heldData = held.createBlockData();
            if (heldData instanceof Bisected || heldData instanceof Bed) {
                return;
            }
        } catch (Throwable ignored) {
            // If block data creation fails for any reason, just don't special-case it.
        }

        Location baseTubeLoc = null;
        BlockFace face = null;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = e.getClickedBlock();
            if (clicked == null) return;

            // Only take over when the clicked block is actually air (selection-box scenario).
            // If it's not air, let vanilla placement work normally.
            if (!clicked.getType().isAir()) return;

            Location maybeTubeLoc = clicked.getLocation();
            if (CloudFrameRegistry.tubes().getTube(maybeTubeLoc) == null) return;

            baseTubeLoc = maybeTubeLoc;
            face = e.getBlockFace();
        } else {
            // RIGHT_CLICK_AIR fallback: raytrace for the tube entity the player is looking at.
            baseTubeLoc = raytraceLookedTube(e.getPlayer());
        }

        if (baseTubeLoc == null || baseTubeLoc.getWorld() == null) return;
        if (face == null) {
            // Compute a face from look direction.
            Location target = computeAdjacentFromLook(baseTubeLoc, e.getPlayer().getEyeLocation().getDirection());
            if (target == null) return;
            placeSimpleBlock(e, handItem, held, target);
            return;
        }

        Location targetLoc = baseTubeLoc.clone().add(face.getModX(), face.getModY(), face.getModZ());
        placeSimpleBlock(e, handItem, held, targetLoc);
    }

    private static void placeSimpleBlock(PlayerInteractEvent e, ItemStack handItem, Material held, Location targetLoc) {
        if (targetLoc == null || targetLoc.getWorld() == null) return;

        // Prevent placing into occupied entity-only blockspaces.
        if (CloudFrameRegistry.tubes().getTube(targetLoc) != null) return;
        if (CloudFrameRegistry.quarries().hasControllerAt(targetLoc)) return;

        Block target = targetLoc.getBlock();
        if (!target.getType().isAir()) return;

        // We are handling this interaction; prevent other plugins from treating this as use-item.
        e.setCancelled(true);

        target.setType(held, true);

        // Best-effort facing like vanilla placement.
        try {
            BlockData data = target.getBlockData();
            if (data instanceof Directional directional) {
                BlockFace facing = e.getPlayer().getFacing().getOppositeFace();
                if (directional.getFaces().contains(facing)) {
                    directional.setFacing(facing);
                    target.setBlockData(directional, true);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            int amt = handItem.getAmount();
            if (amt <= 1) {
                e.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(amt - 1);
                e.getPlayer().getInventory().setItemInMainHand(handItem);
            }
        }
    }

    private static Location raytraceLookedTube(org.bukkit.entity.Player player) {
        if (player == null) return null;
        if (CloudFrameRegistry.tubes().visualsManager() == null) return null;

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        // Don't target through solid blocks; mimic vanilla LOS.
        double limit = 6.0;
        RayTraceResult br = player.getWorld().rayTraceBlocks(eye, dir, limit, FluidCollisionMode.NEVER, true);
        if (br != null && br.getHitPosition() != null) {
            limit = eye.toVector().distance(br.getHitPosition());
            limit = Math.max(0.0, limit - 0.01);
        }

        // Primary: voxel ray-walk (matches vanilla block targeting).
        Location dd = findFirstTubeBlockspaceAlongRay(eye, dir, limit);
        if (dd != null) return dd;

        // Fallback: raytrace the Interaction entity (keep radius small to avoid adjacent tube hits).
        RayTraceResult rr = player.getWorld().rayTraceEntities(
            eye,
            dir,
            limit,
            0.12,
            ent -> (ent instanceof Interaction) && CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(ent.getPersistentDataContainer()) != null
        );

        if (rr == null || rr.getHitEntity() == null) return null;

        return CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(
            rr.getHitEntity().getPersistentDataContainer()
        );
    }

    private static Location findFirstTubeBlockspaceAlongRay(Location eye, Vector dir, double limit) {
        if (eye == null || eye.getWorld() == null || dir == null) return null;
        if (limit <= 0.0) return null;

        Vector d = dir.clone();
        double len = d.length();
        if (len == 0.0) return null;
        d.multiply(1.0 / len);

        double ox = eye.getX() + d.getX() * 0.01;
        double oy = eye.getY() + d.getY() * 0.01;
        double oz = eye.getZ() + d.getZ() * 0.01;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = d.getX() > 0 ? 1 : (d.getX() < 0 ? -1 : 0);
        int stepY = d.getY() > 0 ? 1 : (d.getY() < 0 ? -1 : 0);
        int stepZ = d.getZ() > 0 ? 1 : (d.getZ() < 0 ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getX());
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getY());
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d.getZ());

        double nextVoxelBoundaryX = stepX > 0 ? (x + 1.0) : x;
        double nextVoxelBoundaryY = stepY > 0 ? (y + 1.0) : y;
        double nextVoxelBoundaryZ = stepZ > 0 ? (z + 1.0) : z;

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryX - ox) / d.getX();
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryY - oy) / d.getY();
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (nextVoxelBoundaryZ - oz) / d.getZ();

        if (tMaxX < 0) tMaxX = 0;
        if (tMaxY < 0) tMaxY = 0;
        if (tMaxZ < 0) tMaxZ = 0;

        double t = 0.0;
        while (t <= limit) {
            Location blockLoc = new Location(eye.getWorld(), x, y, z);
            if (CloudFrameRegistry.tubes().getTube(blockLoc) != null) return blockLoc;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxX && tMaxY <= tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceInventoryOnTubeBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (CloudFrameRegistry.tubes() == null) return;

        var clicked = e.getClickedBlock();
        if (clicked == null) return;

        Location baseTubeLoc = clicked.getLocation();
        if (CloudFrameRegistry.tubes().getTube(baseTubeLoc) == null) return;

        ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) return;

        // Let tube placement handler deal with tube items.
        if (CustomBlocks.isTubeItem(handItem)) return;

        Material held = handItem.getType();
        if (held != Material.CHEST && held != Material.TRAPPED_CHEST && held != Material.BARREL) return;

        Block target = clicked.getRelative(e.getBlockFace());
        if (!target.getType().isAir()) return;

        Location targetLoc = target.getLocation();
        if (CloudFrameRegistry.quarries().hasControllerAt(targetLoc)) {
            e.getPlayer().sendMessage("§cYou can't place a chest inside a Quarry Controller.");
            return;
        }

        // Vanilla won't place here because this block is actually air server-side.
        e.setCancelled(true);

        target.setType(held, true);

        // Best-effort facing.
        BlockData data = target.getBlockData();
        if (data instanceof Directional directional) {
            BlockFace facing = e.getPlayer().getFacing().getOppositeFace();
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                target.setBlockData(directional, true);
            }
        }

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            int amt = handItem.getAmount();
            if (amt <= 1) {
                e.getPlayer().getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(amt - 1);
                e.getPlayer().getInventory().setItemInMainHand(handItem);
            }
        }

        // Refresh nearby tubes immediately.
        if (CloudFrameRegistry.tubes().visualsManager() != null) {
            for (Vector v : DIRS) {
                Location adj = targetLoc.clone().add(v);
                if (CloudFrameRegistry.tubes().getTube(adj) != null) {
                    CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(adj);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeftClickTubeBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        var clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Only handle selection-box clicks (server-side this should be air).
        if (!clicked.getType().isAir()) return;

        Location tubeLoc = clicked.getLocation();
        if (CloudFrameRegistry.tubes().getTube(tubeLoc) == null) return;

        // Prevent normal block interaction (this is air server-side anyway).
        e.setCancelled(true);

        debug.log("onLeftClickTubeBlock", "Player " + e.getPlayer().getName() + " removed tube at " + tubeLoc);
        CloudFrameRegistry.tubes().removeTube(tubeLoc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            tubeLoc.getWorld().dropItemNaturally(tubeLoc, CustomBlocks.tubeDrop());
        }

        e.getPlayer().sendMessage("§cTube removed.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageTubeEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof org.bukkit.entity.Player player)) return;
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        var tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(e.getEntity().getPersistentDataContainer());
        if (tubeLoc == null) return;

        // Interaction entities aren't meant to take damage, but cancel regardless.
        e.setCancelled(true);

        if (CloudFrameRegistry.tubes().getTube(tubeLoc) == null) {
            return;
        }

        debug.log("onBreak", "Player " + player.getName() + " removed tube at " + tubeLoc);
        CloudFrameRegistry.tubes().removeTube(tubeLoc);

        if (player.getGameMode() != GameMode.CREATIVE) {
            tubeLoc.getWorld().dropItemNaturally(tubeLoc, CustomBlocks.tubeDrop());
        }

        player.sendMessage("§cTube removed.");
    }

    /**
     * Prevent breaking blocks behind tube entities.
     *
     * If the player is aiming at a tube Interaction but the server would break the block behind it,
     * cancel the block interaction and treat it as a tube break instead.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;
        Location tubeLoc = raytraceTubeLocation(e.getPlayer(), e.getBlock().getLocation());
        if (tubeLoc == null) return;

        // If there is a real block at the tube location (shouldn't happen), let the player damage it.
        if (sameBlockCoords(e.getBlock().getLocation(), tubeLoc) && !e.getBlock().getType().isAir()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;
        Location tubeLoc = raytraceTubeLocation(e.getPlayer(), e.getBlock().getLocation());
        if (tubeLoc == null) return;

        // If there is a real block at the tube location (shouldn't happen), let the player break it.
        if (sameBlockCoords(e.getBlock().getLocation(), tubeLoc) && !e.getBlock().getType().isAir()) return;

        // Cancel breaking the block behind; break the tube instead.
        e.setCancelled(true);

        if (CloudFrameRegistry.tubes().getTube(tubeLoc) == null) return;

        debug.log("onBlockBreak", "Prevented breaking behind tube; breaking tube at " + tubeLoc + " for player=" + e.getPlayer().getName());
        CloudFrameRegistry.tubes().removeTube(tubeLoc);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            tubeLoc.getWorld().dropItemNaturally(tubeLoc, CustomBlocks.tubeDrop());
        }

        e.getPlayer().sendMessage("§cTube removed.");
    }

    private static boolean sameBlockCoords(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private Location raytraceTubeLocation(org.bukkit.entity.Player player, Location breakingBlockLoc) {
        if (player == null || breakingBlockLoc == null || breakingBlockLoc.getWorld() == null) return null;

        double blockDist = player.getEyeLocation().distance(breakingBlockLoc.clone().add(0.5, 0.5, 0.5));
        double maxDist = Math.min(6.0, blockDist + 0.5);

        Location tubeLoc = findFirstTubeBlockspaceAlongRay(player.getEyeLocation(), player.getEyeLocation().getDirection(), maxDist);
        if (tubeLoc == null) return null;

        double tubeDist = player.getEyeLocation().distance(tubeLoc.clone().add(0.5, 0.5, 0.5));
        if (tubeDist > blockDist + 0.25) return null;

        return tubeLoc;
    }

    private void pulseTube(Location loc) {
        debug.log("pulseTube", "Pulsing tube visuals at " + loc);

        var world = loc.getWorld();

        // Pulse at the placed tube
        world.spawnParticle(
            org.bukkit.Particle.END_ROD,
            loc.clone().add(0.5, 0.5, 0.5),
            10, 0.2, 0.2, 0.2, 0.01
        );

        // Pulse along neighbors
        for (var neighbor : CloudFrameRegistry.tubes().getNeighborsOf(loc)) {
            Location nLoc = neighbor.getLocation().clone().add(0.5, 0.5, 0.5);

            debug.log("pulseTube", "Pulsing neighbor tube at " + nLoc);

            world.spawnParticle(
                org.bukkit.Particle.END_ROD,
                nLoc,
                10, 0.2, 0.2, 0.2, 0.01
            );
        }
    }
}
