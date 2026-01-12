package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugManager;

public class MarkerListener implements Listener {

    private static final Debug debug = DebugManager.get(MarkerListener.class);
    private final java.util.Map<java.util.UUID, Long> lastClick = new java.util.HashMap<>();
    private static final long DEBOUNCE_MS = 250;

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        if (!isMarkerTool(item)) {
            return;
        }

        Action action = e.getAction();
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < DEBOUNCE_MS) {
            debug.log("onInteract", "Debounced duplicate marker click for " + p.getName());
            e.setCancelled(true);
            return;
        }
        lastClick.put(p.getUniqueId(), now);

        debug.log("onInteract", "Player " + p.getName() + " used Marker Tool, action=" + action);

        // Only respond to block clicks
        if (e.getClickedBlock() == null) {
            debug.log("onInteract", "Ignored — click was not on a block");
            return;
        }

        Location loc = e.getClickedBlock().getLocation();

        switch (action) {
            case LEFT_CLICK_BLOCK -> {
                debug.log("onInteract", "Setting PosA for " + p.getName() + " at " + loc);
                CloudFrameRegistry.markers().setPosA(p.getUniqueId(), loc);
                p.sendMessage("§bPosition A set.");
                e.setCancelled(true); // prevent breaking
            }

            case RIGHT_CLICK_BLOCK -> {
                debug.log("onInteract", "Setting PosB for " + p.getName() + " at " + loc);
                CloudFrameRegistry.markers().setPosB(p.getUniqueId(), loc);
                p.sendMessage("§bPosition B set.");
                e.setCancelled(true); // prevent block interaction
            }

            default -> {
                debug.log("onInteract", "Ignored action " + action);
            }
        }
    }

    // Prevent block breaking with the marker tool
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (isMarkerTool(item)) {
            debug.log("onBlockBreak", "Cancelled block break by marker tool");
            e.setCancelled(true);
        }
    }

    private boolean isMarkerTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 1002;
    }
}
