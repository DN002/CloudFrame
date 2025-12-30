package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.Vector;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.util.InventoryUtil;

/**
 * Keeps tube visuals in sync when inventories are placed/removed.
 * Example: chest adjacent to a tube should add/remove a connection immediately.
 */
public class InventoryTubeRefreshListener implements Listener {

    private static final Vector[] DIRS = new Vector[] {
        new Vector(1, 0, 0),
        new Vector(-1, 0, 0),
        new Vector(0, 1, 0),
        new Vector(0, -1, 0),
        new Vector(0, 0, 1),
        new Vector(0, 0, -1)
    };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        if (!InventoryUtil.isInventory(placed)) return;
        refreshAdjacentTubesNextTick(placed.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!InventoryUtil.isInventory(block)) return;
        refreshAdjacentTubesNextTick(block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            if (InventoryUtil.isInventory(b)) {
                refreshAdjacentTubesNextTick(b.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            if (InventoryUtil.isInventory(b)) {
                refreshAdjacentTubesNextTick(b.getLocation());
            }
        }
    }

    private static void refreshAdjacentTubesNextTick(Location inventoryLoc) {
        if (inventoryLoc == null || inventoryLoc.getWorld() == null) return;
        if (CloudFrameRegistry.plugin() == null) return;

        // Schedule for next tick so the block state reflects the placement/break.
        CloudFrameRegistry.plugin().getServer().getScheduler().runTask(CloudFrameRegistry.plugin(), () -> {
            var visuals = CloudFrameRegistry.tubes().visualsManager();
            if (visuals == null) return;

            for (Vector v : DIRS) {
                Location adj = inventoryLoc.clone().add(v);
                if (CloudFrameRegistry.tubes().getTube(adj) != null) {
                    visuals.updateTubeAndNeighbors(adj);
                }
            }
        });
    }
}
