package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
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

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.CustomBlocks;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class TubeListener implements Listener {

    private static final Debug debug = DebugManager.get(TubeListener.class);

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
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (CloudFrameRegistry.tubes().visualsManager() == null) return;
        Location tubeLoc = raytraceTubeLocation(e.getPlayer(), e.getBlock().getLocation());
        if (tubeLoc == null) return;

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

    private Location raytraceTubeLocation(org.bukkit.entity.Player player, Location breakingBlockLoc) {
        if (player == null || breakingBlockLoc == null || breakingBlockLoc.getWorld() == null) return null;

        double blockDist = player.getEyeLocation().distance(breakingBlockLoc.clone().add(0.5, 0.5, 0.5));
        double maxDist = Math.min(6.0, blockDist + 0.5);

        RayTraceResult rr = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            maxDist,
            0.2,
            ent -> (ent instanceof Interaction) && CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(ent.getPersistentDataContainer()) != null
        );

        if (rr == null || rr.getHitEntity() == null) return null;

        Location tubeLoc = CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(
            rr.getHitEntity().getPersistentDataContainer()
        );
        if (tubeLoc == null) return null;

        double entityDist = player.getEyeLocation().distance(rr.getHitEntity().getLocation());
        if (entityDist > blockDist + 0.25) return null;

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
