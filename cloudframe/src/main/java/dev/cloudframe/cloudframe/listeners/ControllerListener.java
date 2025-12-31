package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.gui.UnfinalizedControllerGUI;
import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.items.SilkTouchAugment;
import dev.cloudframe.cloudframe.items.SpeedAugment;
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

        // Carry any stored augments from the controller item into this placed controller.
        var stored = CustomBlocks.getStoredAugments(e.getItem());

        debug.log("onPlace", "Player " + e.getPlayer().getName() + " placed controller at " + loc);

        // Track as an unregistered controller so chunk refreshes keep it visible.
        CloudFrameRegistry.quarries().markUnregisteredController(loc, yaw, stored.silkTouch(), stored.speedLevel());
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

        // Carry any stored augments from the controller item into this placed controller.
        var stored = CustomBlocks.getStoredAugments(e.getPlayer().getInventory().getItemInMainHand());

        debug.log("onPlaceOnTube", "Player " + e.getPlayer().getName() + " placed controller at " + targetLoc + " (clicked tube " + tubeLoc + ")");

        CloudFrameRegistry.quarries().markUnregisteredController(targetLoc, yaw, stored.silkTouch(), stored.speedLevel());
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

    /**
     * When using the client-side selection box, the client clicks a fake block at the controller
     * coordinate. Server-side that block is air, so vanilla placement won't work.
     *
     * This handler makes controllers behave like a normal placement anchor: sneak-right-clicking
     * the controller outline with a regular block item places that block into the adjacent blockspace.
     *
     * (Sneak is required so we don't override normal right-click behavior like opening the GUI.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceNormalBlockAgainstController(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        final Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        // Mimic vanilla "place against interactable block" behavior.
        if (!e.getPlayer().isSneaking()) return;

        ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) return;

        // Don't interfere with our own placement items.
        if (CustomBlocks.isControllerItem(handItem)) return;
        if (CustomBlocks.isTubeItem(handItem)) return;

        Material held = handItem.getType();
        if (!held.isBlock()) return;

        // Avoid half-baked multi-block placement (doors, tall flowers, beds, etc.).
        // These should be placed against a real vanilla block so vanilla can handle the second part.
        try {
            BlockData heldData = held.createBlockData();
            if (heldData instanceof Bisected || heldData instanceof Bed) {
                return;
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }

        Location baseControllerLoc = null;
        BlockFace face = null;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = e.getClickedBlock();
            if (clicked == null) return;

            // Only take over when the clicked block is actually air (selection-box scenario).
            // If it's not air, let vanilla placement work normally.
            if (!clicked.getType().isAir()) return;

            Location maybeControllerLoc = clicked.getLocation();
            if (!CloudFrameRegistry.quarries().hasControllerAt(maybeControllerLoc)) return;

            baseControllerLoc = maybeControllerLoc;
            face = e.getBlockFace();
        } else {
            // RIGHT_CLICK_AIR fallback: voxel ray-walk for the controller blockspace.
            baseControllerLoc = raytraceLookedController(e.getPlayer());
        }

        if (baseControllerLoc == null || baseControllerLoc.getWorld() == null) return;

        if (face == null) {
            Location target = computeAdjacentFromLook(baseControllerLoc, e.getPlayer().getEyeLocation().getDirection());
            if (target == null) return;
            placeSimpleBlock(e, handItem, held, target);
            return;
        }

        Location targetLoc = baseControllerLoc.clone().add(face.getModX(), face.getModY(), face.getModZ());
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

        // Match vanilla: don't place if a player is occupying the target blockspace.
        if (isAnyPlayerOccupyingBlockspace(targetLoc)) {
            return;
        }

        target.setType(held, true);

        // Play vanilla-like placement sound (since vanilla placement didn't run).
        try {
            SoundGroup group = target.getBlockData().getSoundGroup();
            Sound sound = group.getPlaceSound();
            targetLoc.getWorld().playSound(targetLoc.clone().add(0.5, 0.5, 0.5), sound, group.getVolume(), group.getPitch());
        } catch (Throwable ignored) {
            // Best-effort fallback.
            try {
                targetLoc.getWorld().playSound(targetLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
            } catch (Throwable ignored2) {
                // ignore
            }
        }

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

    private static boolean isAnyPlayerOccupyingBlockspace(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        double x = loc.getBlockX();
        double y = loc.getBlockY();
        double z = loc.getBlockZ();

        BoundingBox blockBox = new BoundingBox(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        for (Player p : loc.getWorld().getPlayers()) {
            try {
                if (p.getBoundingBox().overlaps(blockBox)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Best-effort.
            }
        }
        return false;
    }

    private static Location raytraceLookedController(org.bukkit.entity.Player player) {
        if (player == null) return null;

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
        Location dd = findFirstControllerBlockspaceAlongRay(eye, dir, limit);
        if (dd != null) return dd;

        // Fallback: raytrace the Interaction entity (keep radius small to avoid adjacent hits).
        if (CloudFrameRegistry.quarries().visualsManager() == null) return null;

        RayTraceResult rr = player.getWorld().rayTraceEntities(
            eye,
            dir,
            limit,
            0.12,
            ent -> (ent instanceof Interaction) && CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(ent.getPersistentDataContainer()) != null
        );

        if (rr == null || rr.getHitEntity() == null) return null;
        return CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(rr.getHitEntity().getPersistentDataContainer());
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
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        var clicked = e.getClickedBlock();
        if (clicked == null) return;

        Location loc = clicked.getLocation();
        if (!CloudFrameRegistry.quarries().hasControllerAt(loc)) return;

        // Let the wrench finalize the controller (don't steal the click).
        var handNow = e.getPlayer().getInventory().getItemInMainHand();
        if (CustomBlocks.isWrenchItem(handNow)) {
            return;
        }

        // Client sometimes refreshes chunk sections on inventory open, which can briefly drop
        // our spoofed BARRIER. Reassert immediately and again next tick.
        ClientSelectionBoxTask.reassertSpoofNow(e.getPlayer(), loc);
        if (CloudFrameRegistry.plugin() != null) {
            Bukkit.getScheduler().runTask(CloudFrameRegistry.plugin(), () ->
                ClientSelectionBoxTask.reassertSpoofNow(e.getPlayer(), loc)
            );
        }

        debug.log("onInteractBlock", "Player " + e.getPlayer().getName() + " interacted with controller block at " + loc);

        // Sneak-right-click with a normal block: place it against the controller like a regular block.
        // This prevents the GUI from opening when the player is just trying to build.
        if (e.getPlayer().isSneaking()) {
            ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
            if (handItem != null && !handItem.getType().isAir()) {
                Material held = handItem.getType();
                boolean isPluginItem = CustomBlocks.isControllerItem(handItem) || CustomBlocks.isTubeItem(handItem);

                if (!isPluginItem && held.isBlock()) {
                    Block target = clicked.getRelative(e.getBlockFace());
                    if (!target.getType().isAir()) {
                        // Behave like vanilla: if occupied, do nothing.
                        e.setCancelled(true);
                        return;
                    }

                    // Use the same manual-placement path as the selection-box anchor, including sound.
                    placeSimpleBlock(e, handItem, held, target.getLocation());
                    return;
                }
            }
        }

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            // Unfinalized controller behavior: augments are stored via right-click with augment in hand.
            var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(loc);
            boolean silk = data != null && data.silkTouch();
            int speed = data != null ? Math.max(0, data.speedLevel()) : 0;

            // If holding an augment, store it now (outside GUI).
            if (SilkTouchAugment.isItem(handNow)) {
                // Non-sneaking controller interaction should behave like a furnace/chest:
                // do NOT attempt to place the held block.
                e.setUseItemInHand(Event.Result.DENY);
                e.setUseInteractedBlock(Event.Result.DENY);
                e.setCancelled(true);

                if (silk) {
                    e.getPlayer().sendMessage("§eSilk Touch augment already stored.");
                    return;
                }

                CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(loc, true, speed);
                consumeHandOne(e.getPlayer());
                e.getPlayer().sendMessage("§aSilk Touch augment stored.");
                return;
            }

            if (SpeedAugment.isItem(handNow)) {
                e.setUseItemInHand(Event.Result.DENY);
                e.setUseInteractedBlock(Event.Result.DENY);
                e.setCancelled(true);

                int newTier = SpeedAugment.getTier(handNow);
                if (newTier <= 0) newTier = 1;

                if (speed == newTier) {
                    e.getPlayer().sendMessage("§eSpeed augment " + roman(newTier) + " already stored.");
                    return;
                }

                // Return previous tier (if any).
                if (speed > 0) {
                    var leftover = e.getPlayer().getInventory().addItem(SpeedAugment.create(speed));
                    leftover.values().forEach(it -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), it));
                }

                CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(loc, silk, newTier);
                consumeHandOne(e.getPlayer());
                e.getPlayer().sendMessage("§aSpeed augment stored (" + roman(newTier) + ").");
                return;
            }

            // Otherwise open limited GUI for viewing/removing stored augments.
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setCancelled(true);
            e.getPlayer().openInventory(UnfinalizedControllerGUI.build(loc, new CustomBlocks.StoredAugments(silk, speed)));
            return;
        }

        // Non-sneaking controller interaction should behave like a furnace/chest:
        // do NOT attempt to place the held block, just open the GUI.
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);

        // Augment install: right-click controller with augment in hand.
        var hand = e.getPlayer().getInventory().getItemInMainHand();
        if (SilkTouchAugment.isItem(hand)) {
            if (q.hasSilkTouchAugment()) {
                e.getPlayer().sendMessage("§eSilk Touch augment already installed.");
                return;
            }

            q.setSilkTouchAugment(true);
            consumeHandOne(e.getPlayer());
            e.getPlayer().sendMessage("§aSilk Touch augment installed.");
            return;
        }

        if (SpeedAugment.isItem(hand)) {
            int newTier = SpeedAugment.getTier(hand);
            if (newTier <= 0) newTier = 1;

            int installed = q.getSpeedAugmentLevel();
            if (installed == newTier) {
                e.getPlayer().sendMessage("§eSpeed augment " + roman(newTier) + " already installed.");
                return;
            }

            // Return previous tier (if any).
            if (installed > 0) {
                var leftover = e.getPlayer().getInventory().addItem(SpeedAugment.create(installed));
                leftover.values().forEach(it -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), it));
            }

            q.setSpeedAugmentLevel(newTier);
            consumeHandOne(e.getPlayer());
            e.getPlayer().sendMessage("§aSpeed augment installed (" + roman(newTier) + ").");
            return;
        }

        e.getPlayer().openInventory(QuarryGUI.build(q));
        ControllerGuiListener.trackGuiOpen(e.getPlayer(), q);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (CloudFrameRegistry.quarries().visualsManager() == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Location loc = CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(
            e.getRightClicked().getPersistentDataContainer()
        );
        if (loc == null) return;

        // Let the wrench finalize the controller (don't steal the click).
        var handNow = e.getPlayer().getInventory().getItemInMainHand();
        if (CustomBlocks.isWrenchItem(handNow)) {
            return;
        }

        ClientSelectionBoxTask.reassertSpoofNow(e.getPlayer(), loc);
        if (CloudFrameRegistry.plugin() != null) {
            Bukkit.getScheduler().runTask(CloudFrameRegistry.plugin(), () ->
                ClientSelectionBoxTask.reassertSpoofNow(e.getPlayer(), loc)
            );
        }

        debug.log("onInteract", "Player " + e.getPlayer().getName() + " interacted with controller entity at " + loc);

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            debug.log("onInteract", "No registered quarry controller at " + loc);

            // Entity-only controller exists but has not been finalized into a quarry yet.
            var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(loc);
            boolean silk = data != null && data.silkTouch();
            int speed = data != null ? Math.max(0, data.speedLevel()) : 0;

            // Store augments via right-click with augment in hand.
            if (SilkTouchAugment.isItem(handNow)) {
                e.setCancelled(true);
                if (silk) {
                    e.getPlayer().sendMessage("§eSilk Touch augment already stored.");
                    return;
                }
                CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(loc, true, speed);
                consumeHandOne(e.getPlayer());
                e.getPlayer().sendMessage("§aSilk Touch augment stored.");
                return;
            }

            if (SpeedAugment.isItem(handNow)) {
                e.setCancelled(true);
                int newTier = SpeedAugment.getTier(handNow);
                if (newTier <= 0) newTier = 1;

                if (speed == newTier) {
                    e.getPlayer().sendMessage("§eSpeed augment " + roman(newTier) + " already stored.");
                    return;
                }

                if (speed > 0) {
                    var leftover = e.getPlayer().getInventory().addItem(SpeedAugment.create(speed));
                    leftover.values().forEach(it -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), it));
                }

                CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(loc, silk, newTier);
                consumeHandOne(e.getPlayer());
                e.getPlayer().sendMessage("§aSpeed augment stored (" + roman(newTier) + ").");
                return;
            }

            // Limited GUI: view/remove only.
            e.getPlayer().openInventory(UnfinalizedControllerGUI.build(loc, new CustomBlocks.StoredAugments(silk, speed)));
            e.setCancelled(true);
            return;
        }

        // Augment install: right-click controller with augment in hand.
        var hand = e.getPlayer().getInventory().getItemInMainHand();
        if (SilkTouchAugment.isItem(hand)) {
            e.setCancelled(true);

            if (q.hasSilkTouchAugment()) {
                e.getPlayer().sendMessage("§eSilk Touch augment already installed.");
                return;
            }

            q.setSilkTouchAugment(true);
            consumeHandOne(e.getPlayer());
            e.getPlayer().sendMessage("§aSilk Touch augment installed.");
            return;
        }

        if (SpeedAugment.isItem(hand)) {
            e.setCancelled(true);

            int newTier = SpeedAugment.getTier(hand);
            if (newTier <= 0) newTier = 1;

            int installed = q.getSpeedAugmentLevel();
            if (installed == newTier) {
                e.getPlayer().sendMessage("§eSpeed augment " + roman(newTier) + " already installed.");
                return;
            }

            // Return previous tier (if any).
            if (installed > 0) {
                var leftover = e.getPlayer().getInventory().addItem(SpeedAugment.create(installed));
                leftover.values().forEach(it -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), it));
            }

            q.setSpeedAugmentLevel(newTier);
            consumeHandOne(e.getPlayer());
            e.getPlayer().sendMessage("§aSpeed augment installed (" + roman(newTier) + ").");
            return;
        }

        e.getPlayer().openInventory(QuarryGUI.build(q));
        ControllerGuiListener.trackGuiOpen(e.getPlayer(), q);
        e.setCancelled(true);
    }

    private static void consumeHandOne(org.bukkit.entity.Player player) {
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        var item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        int amt = item.getAmount();
        if (amt <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amt - 1);
            player.getInventory().setItemInMainHand(item);
        }
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeftClickControllerBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        var clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Only handle selection-box clicks (server-side this should be air).
        if (!clicked.getType().isAir()) return;

        Location loc = clicked.getLocation();
        if (!CloudFrameRegistry.quarries().hasControllerAt(loc)) return;

        // Prevent normal block interaction (this is air server-side anyway).
        e.setCancelled(true);

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE
                && !CustomBlocks.isWrenchItem(e.getPlayer().getInventory().getItemInMainHand())) {
            e.getPlayer().sendMessage("§cYou need a Cloud Wrench to remove controllers.");
            return;
        }

        var player = e.getPlayer();

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            if (!player.isSneaking()) {
                player.sendMessage("§eNot finalized. Use a §6Wrench§e to finalize, or §cSneak + hit§e to pick it up.");
                return;
            }

            var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(loc);

            CloudFrameRegistry.quarries().unmarkUnregisteredController(loc);

            refreshAdjacentTubes(loc);
            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                CloudFrameRegistry.quarries().visualsManager().removeController(loc);
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                boolean silk = data != null && data.silkTouch();
                int speed = data != null ? data.speedLevel() : 0;
                loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDropWithStoredAugments(silk, speed));
            }

            player.sendMessage("§cController picked up.");
            return;
        }

        if (!player.isSneaking()) {
            player.sendMessage("§cSneak + hit to remove the quarry.");
            return;
        }

        CloudFrameRegistry.quarries().remove(q);
        refreshAdjacentTubes(loc);

        if (player.getGameMode() != GameMode.CREATIVE) {
            loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDropWithStoredAugments(q.hasSilkTouchAugment(), q.getSpeedAugmentLevel()));
        }

        player.sendMessage("§cQuarry removed.");
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

        if (player.getGameMode() != GameMode.CREATIVE
                && !CustomBlocks.isWrenchItem(player.getInventory().getItemInMainHand())) {
            player.sendMessage("§cYou need a Cloud Wrench to remove controllers.");
            return;
        }

        Quarry q = CloudFrameRegistry.quarries().getByController(loc);
        if (q == null) {
            // Unregistered controller entity: allow sneak-hit to remove and get the item back.
            if (!player.isSneaking()) {
                player.sendMessage("§eNot finalized. Use a §6Wrench§e to finalize, or §cSneak + hit§e to pick it up.");
                return;
            }

            var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(loc);

            CloudFrameRegistry.quarries().unmarkUnregisteredController(loc);

            // Update nearby tubes immediately so they disconnect visually.
            refreshAdjacentTubes(loc);

            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                CloudFrameRegistry.quarries().visualsManager().removeController(loc);
            }

            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                boolean silk = data != null && data.silkTouch();
                int speed = data != null ? data.speedLevel() : 0;
                loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDropWithStoredAugments(silk, speed));
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
            loc.getWorld().dropItemNaturally(loc, CustomBlocks.controllerDropWithStoredAugments(q.hasSilkTouchAugment(), q.getSpeedAugmentLevel()));
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

        if (e.getPlayer().getGameMode() != GameMode.CREATIVE
                && !CustomBlocks.isWrenchItem(e.getPlayer().getInventory().getItemInMainHand())) {
            e.getPlayer().sendMessage("§cYou need a Cloud Wrench to remove controllers.");
            return;
        }

        Location controllerLoc = shim.controllerLoc;
        if (controllerLoc == null) return;

        var player = e.getPlayer();

        Quarry q = CloudFrameRegistry.quarries().getByController(controllerLoc);
        if (q == null) {
            if (!player.isSneaking()) {
                player.sendMessage("§eNot finalized. Use a §6Wrench§e to finalize, or §cSneak + hit§e to pick it up.");
                return;
            }

            var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(controllerLoc);

            CloudFrameRegistry.quarries().unmarkUnregisteredController(controllerLoc);

            refreshAdjacentTubes(controllerLoc);
            if (CloudFrameRegistry.quarries().visualsManager() != null) {
                CloudFrameRegistry.quarries().visualsManager().removeController(controllerLoc);
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                boolean silk = data != null && data.silkTouch();
                int speed = data != null ? data.speedLevel() : 0;
                controllerLoc.getWorld().dropItemNaturally(controllerLoc, CustomBlocks.controllerDropWithStoredAugments(silk, speed));
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
            controllerLoc.getWorld().dropItemNaturally(controllerLoc, CustomBlocks.controllerDropWithStoredAugments(q.hasSilkTouchAugment(), q.getSpeedAugmentLevel()));
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

    private static void dropInstalledAugments(Quarry q, Location dropLoc) {
        if (q == null || dropLoc == null || dropLoc.getWorld() == null) return;

        if (q.hasSilkTouchAugment()) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, SilkTouchAugment.create());
        }

        int speed = q.getSpeedAugmentLevel();
        if (speed > 0) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, SpeedAugment.create(speed));
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

        Location controllerLoc = findFirstControllerBlockspaceAlongRay(player.getEyeLocation(), player.getEyeLocation().getDirection(), maxDist);
        if (controllerLoc == null) return null;

        double ctrlDist = player.getEyeLocation().distance(controllerLoc.clone().add(0.5, 0.5, 0.5));
        if (ctrlDist > blockDist + 0.25) return null;

        return new PlayerInteractEntityEventShim(controllerLoc);
    }

    private static Location findFirstControllerBlockspaceAlongRay(Location eye, Vector dir, double limit) {
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
            if (CloudFrameRegistry.quarries().hasControllerAt(blockLoc)) return blockLoc;

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
}
