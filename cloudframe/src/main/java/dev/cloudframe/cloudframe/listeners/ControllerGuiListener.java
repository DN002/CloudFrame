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

        // --- Pause Quarry ---
        if (name.contains("Pause")) {
            q.setActive(false);
            p.sendMessage("§cQuarry paused.");
            // GUI will update automatically via update task
            return;
        }

        // --- Resume Quarry ---
        if (name.contains("Resume")) {
            q.setActive(true);
            p.sendMessage("§aQuarry resumed.");
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
