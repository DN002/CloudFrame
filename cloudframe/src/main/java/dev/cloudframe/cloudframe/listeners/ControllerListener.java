package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.util.CustomBlocks;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class ControllerListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerListener.class);

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!CustomBlocks.isControllerItem(e.getItem())) return;

        var clicked = e.getClickedBlock();
        if (clicked == null) return;

        var target = clicked.getRelative(e.getBlockFace());
        if (!target.getType().isAir()) return;

        Location loc = target.getLocation();

        // Always prevent vanilla copper-block placement; this item is entity-only.
        e.setCancelled(true);

        // Don't allow stacking controllers or placing into a tube.
        if (CloudFrameRegistry.quarries().hasControllerAt(loc)) {
            e.getPlayer().sendMessage("§cA Quarry Controller already occupies this block.");
            return;
        }
        if (CloudFrameRegistry.tubes().getTube(loc) != null) {
            e.getPlayer().sendMessage("§cYou can't place a Quarry Controller inside a tube.");
            return;
        }

        int yaw = snapYaw(e.getPlayer().getLocation().getYaw());

        debug.log("onPlace", "Player " + e.getPlayer().getName() + " placed controller at " + loc);

        // Track as an unregistered controller so chunk refreshes keep it visible.
        CloudFrameRegistry.quarries().markUnregisteredController(loc, yaw);
        refreshAdjacentTubes(loc);

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

        e.getPlayer().sendMessage("§6Quarry Controller placed.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceOnTube(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!CustomBlocks.isControllerItem(e.getPlayer().getInventory().getItemInMainHand())) return;

        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        var tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(
            e.getRightClicked().getPersistentDataContainer()
        );
        if (tubeLoc == null) return;

        e.setCancelled(true);

        Location targetLoc = computeTargetFromEntityClick(e.getPlayer(), e.getRightClicked(), tubeLoc);
        if (targetLoc == null) return;

        if (!targetLoc.getChunk().isLoaded()) return;
        if (!targetLoc.getBlock().getType().isAir()) return;

        // Don't allow stacking controllers or placing into a tube.
        if (CloudFrameRegistry.quarries().hasControllerAt(targetLoc)) {
            e.getPlayer().sendMessage("§cA Quarry Controller already occupies this block.");
            return;
        }
        if (CloudFrameRegistry.tubes().getTube(targetLoc) != null) {
            e.getPlayer().sendMessage("§cYou can't place a Quarry Controller inside a tube.");
            return;
        }

        int yaw = snapYaw(e.getPlayer().getLocation().getYaw());

        debug.log("onPlaceOnTube", "Player " + e.getPlayer().getName() + " placed controller at " + targetLoc + " (clicked tube " + tubeLoc + ")");

        CloudFrameRegistry.quarries().markUnregisteredController(targetLoc, yaw);
        refreshAdjacentTubes(targetLoc);

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

        e.getPlayer().sendMessage("§6Quarry Controller placed.");
    }

    private static int snapYaw(float yaw) {
        float y = yaw % 360.0f;
        if (y < 0.0f) y += 360.0f;
        int snapped = Math.round(y / 90.0f) * 90;
        snapped %= 360;
        return snapped;
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
            ent -> ent.getUniqueId().equals(clicked.getUniqueId())
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
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (CloudFrameRegistry.quarries().visualsManager() == null) return;

        Location loc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(
            e.getRightClicked().getPersistentDataContainer()
        );
        if (loc == null) return;

        debug.log("onInteract", "Player " + e.getPlayer().getName() + " interacted with controller entity at " + loc);

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            debug.log("onInteract", "No registered quarry controller at " + loc);

            // Entity-only controller exists but has not been finalized into a quarry yet.
            e.getPlayer().sendMessage("§eController not finalized. Use a §6Wrench§e on it to finalize.");
            e.setCancelled(true);
            return;
        }

        e.getPlayer().openInventory(QuarryGUI.build(q));
        ControllerGuiListener.trackGuiOpen(e.getPlayer(), q);
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageControllerEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof org.bukkit.entity.Player player)) return;
        if (CloudFrameRegistry.quarries().visualsManager() == null) return;

        Location loc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(
            e.getEntity().getPersistentDataContainer()
        );
        if (loc == null) return;

        // Prevent entity damage behavior.
        e.setCancelled(true);

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            // Unregistered controller entity: allow sneak-hit to remove and get the item back.
            if (!player.isSneaking()) {
                player.sendMessage("§eNot finalized. Use a §6Wrench§e to finalize, or §cSneak + hit§e to pick it up.");
                return;
            }

            CloudFrameRegistry.quarries().unmarkUnregisteredController(loc);

            // Update nearby tubes immediately so they disconnect visually.
            refreshAdjacentTubes(loc);

            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                CloudFrameRegistry.quarries().visualsManager().removeController(loc);
            }

            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDrop());
            }

            player.sendMessage("§cController picked up.");
            return;
        }

        if (!player.isSneaking()) {
            player.sendMessage("§cSneak + hit to remove the quarry.");
            return;
        }

        debug.log("onBreak", "Removing quarry for owner=" + q.getOwner());
        CloudFrameRegistry.quarries().remove(q);

        // Update nearby tubes immediately so they disconnect visually.
        refreshAdjacentTubes(loc);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDrop());
        }

        player.sendMessage("§cQuarry removed.");
    }

    /**
     * If a player tries to break a block "through" a controller, cancel the block interaction.
     * This prevents the block behind from being damaged/broken when the player is aiming at the
     * controller model.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (CloudFrameRegistry.quarries().visualsManager() == null) return;

        PlayerInteractEntityEventShim shim = raytraceControllerInteraction(e.getPlayer(), e.getBlock().getLocation());
        if (shim == null) return;

        // Stop the client-side block cracking from ever starting.
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (CloudFrameRegistry.quarries().visualsManager() == null) return;

        PlayerInteractEntityEventShim shim = raytraceControllerInteraction(e.getPlayer(), e.getBlock().getLocation());
        if (shim == null) return;

        // Cancel the block break and instead treat it as an attempt to break the controller.
        e.setCancelled(true);

        Location controllerLoc = shim.controllerLoc;
        if (controllerLoc == null) return;

        var player = e.getPlayer();

        Quarry q = CloudFrameRegistry.quarries().getByController(controllerLoc);
        if (q == null) {
            if (!player.isSneaking()) {
                player.sendMessage("§eNot finalized. Use a §6Wrench§e to finalize, or §cSneak + hit§e to pick it up.");
                return;
            }

            CloudFrameRegistry.quarries().unmarkUnregisteredController(controllerLoc);

            refreshAdjacentTubes(controllerLoc);
            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                CloudFrameRegistry.quarries().visualsManager().removeController(controllerLoc);
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                controllerLoc.getWorld().dropItemNaturally(controllerLoc, CustomBlocks.controllerDrop());
            }

            player.sendMessage("§cController picked up.");
            return;
        }

        if (!player.isSneaking()) {
            player.sendMessage("§cSneak + hit to remove the quarry.");
            return;
        }

        CloudFrameRegistry.quarries().remove(q);

        refreshAdjacentTubes(controllerLoc);
        if (player.getGameMode() != GameMode.CREATIVE) {
            controllerLoc.getWorld().dropItemNaturally(controllerLoc, CustomBlocks.controllerDrop());
        }

        player.sendMessage("§cQuarry removed.");
    }

    private static void refreshAdjacentTubes(Location controllerLoc) {
        if (controllerLoc == null || controllerLoc.getWorld() == null) return;
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;

        final Vector[] DIRS = new Vector[] {
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0),
            new Vector(0, 1, 0),
            new Vector(0, -1, 0),
            new Vector(0, 0, 1),
            new Vector(0, 0, -1)
        };

        for (Vector v : DIRS) {
            Location adj = controllerLoc.clone().add(v);
            if (CloudFrameRegistry.tubes().getTube(adj) != null) {
                CloudFrameRegistry.tubes().visualsManager().updateTubeAndNeighbors(adj);
            }
        }
    }

    private static final class PlayerInteractEntityEventShim {
        final Location controllerLoc;

        private PlayerInteractEntityEventShim(Location controllerLoc) {
            this.controllerLoc = controllerLoc;
        }
    }

    private PlayerInteractEntityEventShim raytraceControllerInteraction(org.bukkit.entity.Player player, Location breakingBlockLoc) {
        if (player == null || breakingBlockLoc == null || breakingBlockLoc.getWorld() == null) return null;

        // Approximate distance to the block center being broken.
        double blockDist = player.getEyeLocation().distance(breakingBlockLoc.clone().add(0.5, 0.5, 0.5));
        double maxDist = Math.min(6.0, blockDist + 0.5);

        RayTraceResult rr = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            maxDist,
            0.2,
            ent -> (ent instanceof Interaction) && CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(ent.getPersistentDataContainer()) != null
        );

        if (rr == null || rr.getHitEntity() == null) return null;

        Location controllerLoc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(
            rr.getHitEntity().getPersistentDataContainer()
        );
        if (controllerLoc == null) return null;

        // If the controller is basically the thing you're aiming at (i.e. closer than the block), treat it as a controller hit.
        double entityDist = player.getEyeLocation().distance(rr.getHitEntity().getLocation());
        if (entityDist > blockDist + 0.25) return null;

        return new PlayerInteractEntityEventShim(controllerLoc);
    }
}
