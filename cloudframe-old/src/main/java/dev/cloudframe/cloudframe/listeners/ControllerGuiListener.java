package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import dev.cloudframe.cloudframe.gui.QuarryGUI;
import dev.cloudframe.cloudframe.gui.QuarryHolder;
import dev.cloudframe.cloudframe.quarry.Quarry;
import dev.cloudframe.cloudframe.items.SilkTouchAugment;
import dev.cloudframe.cloudframe.items.SpeedAugment;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

import java.util.HashMap;
import java.util.Map;

public class ControllerGuiListener implements Listener {

    private static final Debug debug = DebugManager.get(ControllerGuiListener.class);
    
    // Track players viewing Quarry GUIs and their associated quarries
    private static final Map<Player, Quarry> openGuis = new HashMap<>();
    private static BukkitTask updateTask;

    /**
     * Initialize the GUI update task. Call this from plugin onEnable.
     */
    public static void startGuiUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(
            dev.cloudframe.cloudframe.core.CloudFrameRegistry.plugin(),
            ControllerGuiListener::updateAllOpenGuis,
            1L,  // Start after 1 tick
            2L   // Update every 2 ticks (0.1 seconds)
        );
        Debug d = DebugManager.get(ControllerGuiListener.class);
        d.log("startGuiUpdateTask", "GUI update task started");
    }

    /**
     * Stop the GUI update task. Call this from plugin onDisable.
     */
    public static void stopGuiUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
            Debug d = DebugManager.get(ControllerGuiListener.class);
            d.log("stopGuiUpdateTask", "GUI update task stopped");
        }
    }

    /**
     * Update all open Quarry GUIs with fresh data.
     */
    private static void updateAllOpenGuis() {
        // Copy the map to avoid concurrent modification
        Map<Player, Quarry> snapshot = new HashMap<>(openGuis);
        
        for (Map.Entry<Player, Quarry> entry : snapshot.entrySet()) {
            Player p = entry.getKey();
            Quarry q = entry.getValue();
            
            // Verify player is still online and viewing the GUI
            if (!p.isOnline() || !p.getOpenInventory().getTitle().equals("Quarry Controller")) {
                openGuis.remove(p);
                continue;
            }
            
            // Update the inventory in place
            Inventory currentInv = p.getOpenInventory().getTopInventory();
            Inventory freshInv = QuarryGUI.build(q);
            
            // Copy items from fresh inventory to current inventory
            for (int i = 0; i < Math.min(27, freshInv.getSize()); i++) {
                currentInv.setItem(i, freshInv.getItem(i));
            }
        }
    }

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;

        // Only handle our GUI
        if (!e.getView().getTitle().equals("Quarry Controller")) return;

        e.setCancelled(true);

        // Ignore clicks in the player inventory
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) {
            return;
        }

        // Retrieve quarry from GUI holder
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof QuarryHolder qh)) {
            debug.log("onInventoryClick", "Inventory holder is not QuarryHolder");
            return;
        }

        Quarry q = qh.getQuarry();
        if (q == null) {
            debug.log("onInventoryClick", "QuarryHolder returned null quarry");
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }

        String name = clicked.getItemMeta().getDisplayName();
        debug.log("onInventoryClick", "Clicked item name=" + name);

        // --- Augment slots ---
        int rawSlot = e.getRawSlot();
        if (rawSlot == 15) {
            handleSilkAugmentClick(e, p, q);
            return;
        }
        if (rawSlot == 16) {
            handleSpeedAugmentClick(e, p, q);
            return;
        }

        // --- Output routing mode ---
        if (rawSlot == 8) {
            boolean next = !q.isOutputRoundRobin();
            q.setOutputRoundRobin(next);
            p.sendMessage("§bOutput routing: " + (next ? "§aRound Robin" : "§eFill First"));
            dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().saveAll();
            return;
        }

        // --- Toggle Quarry (Lever in slot 7) ---
        if (rawSlot == 7 || name.contains("Pause") || name.contains("Resume")) {
            if (q.isActive()) {
                q.setActive(false);
                p.sendMessage("§cQuarry paused.");
                dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().saveAll();
            } else {
                if (!q.hasValidOutput()) {
                    p.sendMessage("§cNo valid output: connect tubes to a chest (inventory) before starting.");
                    return;
                }
                q.setActive(true);
                p.sendMessage("§aQuarry resumed.");
                dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().saveAll();
            }
            // GUI will update automatically via update task
            return;
        }

        // --- Refresh Metadata ---
            if (name.contains("Refresh Metadata")) {
                // Reset mining progress and restart the scan
                q.resetMetadataAndProgress();
                p.sendMessage("§bRefreshing metadata and resetting progress...");
                // GUI will update automatically via update task
                return;
            }

        // --- Remove Quarry ---
        if (name.contains("Remove")) {
            p.sendMessage("§4Quarry removed.");

            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && q.getController() != null && q.getController().getWorld() != null) {
                if (q.hasSilkTouchAugment()) {
                    q.getController().getWorld().dropItemNaturally(q.getController(), SilkTouchAugment.create());
                }
                int speed = q.getSpeedAugmentLevel();
                if (speed > 0) {
                    q.getController().getWorld().dropItemNaturally(q.getController(), SpeedAugment.create(speed));
                }

                // Also drop the controller item itself.
                q.getController().getWorld().dropItemNaturally(q.getController(), dev.cloudframe.cloudframe.util.CustomBlocks.controllerDrop());
            }

            dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().remove(q);

            // Ensure nearby tube visuals update immediately.
            if (q.getController() != null) {
                var tubeVisuals = dev.cloudframe.cloudframe.core.CloudFrameRegistry.tubes().visualsManager();
                if (tubeVisuals != null) {
                    org.bukkit.util.Vector[] dirs = new org.bukkit.util.Vector[] {
                        new org.bukkit.util.Vector(1, 0, 0),
                        new org.bukkit.util.Vector(-1, 0, 0),
                        new org.bukkit.util.Vector(0, 1, 0),
                        new org.bukkit.util.Vector(0, -1, 0),
                        new org.bukkit.util.Vector(0, 0, 1),
                        new org.bukkit.util.Vector(0, 0, -1)
                    };
                    for (org.bukkit.util.Vector v : dirs) {
                        var adj = q.getController().clone().add(v);
                        if (dev.cloudframe.cloudframe.core.CloudFrameRegistry.tubes().getTube(adj) != null) {
                            tubeVisuals.updateTubeAndNeighbors(adj);
                        }
                    }
                }
            }

            p.closeInventory();
            return;
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getView().getTitle().equals("Quarry Controller")) return;
        // Block ALL dragging in the GUI (top inventory and player inventory)
        e.setCancelled(true);
    }

    private static void handleSilkAugmentClick(InventoryClickEvent e, Player p, Quarry q) {
        // Remove (click to remove)
        if (q.hasSilkTouchAugment()) {
            q.setSilkTouchAugment(false);
            giveOrDrop(p, SilkTouchAugment.create());
            p.sendMessage("§eSilk Touch augment removed.");
            return;
        }

        // Install via controller right-click (not via GUI)
        p.sendMessage("§7To install: right-click the controller with a Silk Touch Augment in hand.");
    }

    private static void handleSpeedAugmentClick(InventoryClickEvent e, Player p, Quarry q) {
        int installed = q.getSpeedAugmentLevel();

        // Remove (click to remove)
        if (installed > 0) {
            q.setSpeedAugmentLevel(0);
            giveOrDrop(p, SpeedAugment.create(installed));
            p.sendMessage("§eSpeed augment removed (" + roman(installed) + ").");
            return;
        }

        // Install via controller right-click (not via GUI)
        p.sendMessage("§7To install: right-click the controller with a Speed Augment in hand.");
    }

    private static void giveOrDrop(Player p, ItemStack item) {
        if (p == null || item == null || item.getType().isAir()) return;
        var leftover = p.getInventory().addItem(item);
        leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private static void consumeCursorOne(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        int amt = cursor.getAmount();
        if (amt <= 1) {
            e.setCursor(null);
        } else {
            cursor.setAmount(amt - 1);
            e.setCursor(cursor);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        
        // Remove player from tracking if they close a Quarry Controller GUI
        if (e.getView().getTitle().equals("Quarry Controller")) {
            openGuis.remove(p);
            debug.log("onInventoryClose", "Removed player " + p.getName() + " from GUI tracking");
        }
    }

    /**
     * Track when a player opens the Quarry Controller GUI.
     * Call this when the GUI is opened.
     */
    public static void trackGuiOpen(Player p, Quarry q) {
        openGuis.put(p, q);
    }
}
