package dev.cloudframe.cloudframe.util;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.cloudframe.cloudframe.items.QuarryControllerBlock;
import dev.cloudframe.cloudframe.items.TubeItem;

public final class CustomBlocks {

    private CustomBlocks() {}

    // Item identity (CustomModelData)
    private static final int TUBE_CMD = 1001;
    private static final int CONTROLLER_CMD = 1004;

    // NOTE: Controllers are entity-only now. We keep this constant only to identify
    // legacy note blocks during migration/cleanup.
    private static final int CONTROLLER_NOTE_ID = 2;

    public static boolean isLegacyControllerNoteBlock(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK) return false;
        try {
            var data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.type.NoteBlock nb) {
                return nb.getNote().getId() == CONTROLLER_NOTE_ID;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isTubeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta.hasCustomModelData() && meta.getCustomModelData() == TUBE_CMD) return true;
        return meta.hasDisplayName() && meta.getDisplayName().contains("Cloud Tube");
    }

    public static boolean isControllerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta.hasCustomModelData() && meta.getCustomModelData() == CONTROLLER_CMD) return true;
        return meta.hasDisplayName() && meta.getDisplayName().contains("Quarry Controller");
    }

    public static ItemStack tubeDrop() {
        return TubeItem.create();
    }

    public static ItemStack controllerDrop() {
        return QuarryControllerBlock.create();
    }

    public static void consumePlacedItem(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack stack = p.getInventory().getItem(hand);
        if (stack == null || stack.getType().isAir()) return;

        int amt = stack.getAmount();
        if (amt <= 1) {
            p.getInventory().setItem(hand, null);
        } else {
            stack.setAmount(amt - 1);
            p.getInventory().setItem(hand, stack);
        }
    }
}
