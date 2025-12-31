package dev.cloudframe.cloudframe.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.block.Action;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents players from actually flying when we temporarily grant allowFlight
 * as a safety-bypass for client-side collision spoofing.
 */
public class FlightBypassListener implements Listener {

    // Small cooldown to prevent spammy right-click packets from repeatedly triggering
    // client-side spoof drops while holding right click on virtual blocks.
    private static final long VIRTUAL_RIGHT_CLICK_COOLDOWN_NS = 420_000_000L; // ~420ms
    private static final Map<UUID, Long> lastVirtualRightClickNs = new HashMap<>();

    private static boolean isVirtualTargetEntity(org.bukkit.entity.Entity ent) {
        if (ent == null) return false;
        var pdc = ent.getPersistentDataContainer();

        try {
            if (dev.cloudframe.cloudframe.core.CloudFrameRegistry.tubes() != null
                && dev.cloudframe.cloudframe.core.CloudFrameRegistry.tubes().visualsManager() != null
                && dev.cloudframe.cloudframe.core.CloudFrameRegistry.tubes().visualsManager().getTaggedTubeLocation(pdc) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }

        try {
            if (dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries() != null
                && dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().visualsManager() != null
                && dev.cloudframe.cloudframe.core.CloudFrameRegistry.quarries().visualsManager().getTaggedControllerLocation(pdc) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }

        return false;
    }

    private static boolean shouldThrottleVirtualRightClick(Player p) {
        if (p == null) return false;

        // Only throttle when the player is actually standing on a virtual support.
        // This keeps normal gameplay responsive.
        if (!ClientSelectionBoxTask.isFlightBypassActive(p)) return false;

        long now = System.nanoTime();
        UUID id = p.getUniqueId();
        Long last = lastVirtualRightClickNs.get(id);
        if (last != null && (now - last) < VIRTUAL_RIGHT_CLICK_COOLDOWN_NS) {
            return true;
        }
        lastVirtualRightClickNs.put(id, now);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getPlayer() == null) return;

        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            // If the client is right-clicking a spoofed BARRIER (server sees AIR), Bukkit
            // can surface it as RIGHT_CLICK_BLOCK with an AIR clicked block.
            if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                Location cl = e.getClickedBlock().getLocation();
                if (e.getClickedBlock().getType().isAir() && ClientSelectionBoxTask.isSpoofedFor(e.getPlayer(), cl)) {
                    if (shouldThrottleVirtualRightClick(e.getPlayer())) {
                        e.setCancelled(true);
                        ClientSelectionBoxTask.requestRefreshBurst(e.getPlayer());
                        ClientSelectionBoxTask.forceRefreshNow(e.getPlayer());
                        return;
                    }
                }
            }

            ClientSelectionBoxTask.requestRefreshBurst(e.getPlayer());

            // Immediate + end-of-tick refresh to minimize any visible blip.
            ClientSelectionBoxTask.forceRefreshNow(e.getPlayer());
            if (org.bukkit.Bukkit.getPluginManager() != null) {
                var pl = dev.cloudframe.cloudframe.core.CloudFrameRegistry.plugin();
                if (pl != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(pl, () -> ClientSelectionBoxTask.forceRefreshNow(e.getPlayer()));
                    org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> ClientSelectionBoxTask.forceRefreshNow(e.getPlayer()), 1L);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getPlayer() == null) return;

        // Only throttle interactions with our virtual tube/controller entities.
        if (isVirtualTargetEntity(e.getRightClicked())) {
            if (shouldThrottleVirtualRightClick(e.getPlayer())) {
                e.setCancelled(true);
                ClientSelectionBoxTask.requestRefreshBurst(e.getPlayer());
                ClientSelectionBoxTask.forceRefreshNow(e.getPlayer());
                return;
            }
        }

        ClientSelectionBoxTask.requestRefreshBurst(e.getPlayer());

        ClientSelectionBoxTask.forceRefreshNow(e.getPlayer());
        var pl = dev.cloudframe.cloudframe.core.CloudFrameRegistry.plugin();
        if (pl != null) {
            org.bukkit.Bukkit.getScheduler().runTask(pl, () -> ClientSelectionBoxTask.forceRefreshNow(e.getPlayer()));
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> ClientSelectionBoxTask.forceRefreshNow(e.getPlayer()), 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        if (!ClientSelectionBoxTask.isFlightBypassActive(p)) return;

        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;

        // We only enable allowFlight to avoid "floating too long" kicks.
        // Don't let the player actually start flying.
        e.setCancelled(true);
        try {
            p.setFlying(false);
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() == null) return;
        lastVirtualRightClickNs.remove(e.getPlayer().getUniqueId());
        ClientSelectionBoxTask.cleanupFor(e.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        if (e.getPlayer() == null) return;
        lastVirtualRightClickNs.remove(e.getPlayer().getUniqueId());
        ClientSelectionBoxTask.cleanupFor(e.getPlayer());
    }
}
