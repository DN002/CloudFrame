package dev.cloudframe.cloudframe.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.core.CloudFrameRegistry;
import dev.cloudframe.cloudframe.gui.UnfinalizedControllerGUI;
import dev.cloudframe.cloudframe.gui.UnfinalizedControllerHolder;
import dev.cloudframe.cloudframe.items.SilkTouchAugment;
import dev.cloudframe.cloudframe.items.SpeedAugment;
import dev.cloudframe.cloudframe.util.CustomBlocks;

public class UnfinalizedControllerGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;

        if (!e.getView().getTitle().equals("Quarry Controller (Unfinalized)")) return;

        // Only allow interactions we explicitly handle.
        e.setCancelled(true);

        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof UnfinalizedControllerHolder uh)) return;

        var controllerLoc = uh.getControllerLoc();
        if (controllerLoc == null || controllerLoc.getWorld() == null) return;

        // If this controller became finalized while GUI is open, close it.
        if (CloudFrameRegistry.quarries().getByController(controllerLoc) != null) {
            p.closeInventory();
            return;
        }

        int rawSlot = e.getRawSlot();
        if (rawSlot != 15 && rawSlot != 16) return;

        var current = CloudFrameRegistry.quarries().getUnregisteredControllerData(controllerLoc);
        boolean silk = current != null && current.silkTouch();
        int speed = current != null ? Math.max(0, current.speedLevel()) : 0;

        ItemStack cursor = e.getCursor();

        if (rawSlot == 15) {
            // Remove
            if (silk) {
                CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(controllerLoc, false, speed);
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    giveOrDrop(p, SilkTouchAugment.create());
                }
                refresh(p, controllerLoc);
                p.sendMessage("§eSilk Touch augment removed (stored)." );
                return;
            }

            // Add is not supported via GUI (matches finalized behavior).
            if (SilkTouchAugment.isItem(cursor)) {
                p.sendMessage("§7To add: right-click the controller with the augment in hand.");
            }
            return;
        }

        // rawSlot == 16
        if (speed > 0) {
            CloudFrameRegistry.quarries().updateUnregisteredControllerAugments(controllerLoc, silk, 0);
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                giveOrDrop(p, SpeedAugment.create(speed));
            }
            refresh(p, controllerLoc);
            p.sendMessage("§eSpeed augment removed (stored)." );
            return;
        }

        // Add is not supported via GUI (matches finalized behavior).
        if (SpeedAugment.isItem(cursor)) {
            p.sendMessage("§7To add: right-click the controller with the augment in hand.");
        }
    }

    private static void refresh(Player p, org.bukkit.Location controllerLoc) {
        var data = CloudFrameRegistry.quarries().getUnregisteredControllerData(controllerLoc);
        boolean silk = data != null && data.silkTouch();
        int speed = data != null ? Math.max(0, data.speedLevel()) : 0;
        var fresh = UnfinalizedControllerGUI.build(controllerLoc, new CustomBlocks.StoredAugments(silk, speed));
        var top = p.getOpenInventory().getTopInventory();
        for (int i = 0; i < Math.min(top.getSize(), fresh.getSize()); i++) {
            top.setItem(i, fresh.getItem(i));
        }
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
}
