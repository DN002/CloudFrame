package dev.cloudframe.cloudframe.util;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.cloudframe.cloudframe.items.QuarryControllerBlock;
import dev.cloudframe.cloudframe.items.TubeItem;

public final class CustomBlocks {

    private CustomBlocks() {}

    // Item identity (CustomModelData)
    private static final int TUBE_CMD = 1001;
    private static final int CONTROLLER_CMD = 1004;
    private static final int WRENCH_CMD = 1003;

    // NOTE: Controllers are entity-only now. We keep this constant only to identify
    // legacy note blocks during migration/cleanup.
    private static final int CONTROLLER_NOTE_ID = 2;

    // Controller item stored state
    private static final NamespacedKey CTRL_SILK_KEY = new NamespacedKey("cloudframe", "controller_silk_touch");
    private static final NamespacedKey CTRL_SPEED_KEY = new NamespacedKey("cloudframe", "controller_speed_level");

    public static record StoredAugments(boolean silkTouch, int speedLevel) {
        public boolean hasAny() {
            return silkTouch || speedLevel > 0;
        }
    }

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

    public static boolean isWrenchItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();

        if (meta.hasCustomModelData() && meta.getCustomModelData() == WRENCH_CMD) return true;
        return meta.hasDisplayName() && meta.getDisplayName().contains("Cloud Wrench");
    }

    public static ItemStack tubeDrop() {
        return TubeItem.create();
    }

    public static ItemStack controllerDrop() {
        return QuarryControllerBlock.create();
    }

    public static ItemStack controllerDropWithStoredAugments(boolean silkTouch, int speedLevel) {
        ItemStack item = QuarryControllerBlock.create();
        applyStoredAugments(item, silkTouch, speedLevel);
        return item;
    }

    public static StoredAugments getStoredAugments(ItemStack item) {
        if (!isControllerItem(item)) return new StoredAugments(false, 0);
        if (!item.hasItemMeta()) return new StoredAugments(false, 0);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new StoredAugments(false, 0);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean silk = false;
        Integer silkVal = pdc.get(CTRL_SILK_KEY, PersistentDataType.INTEGER);
        if (silkVal != null) silk = silkVal.intValue() == 1;

        int speed = 0;
        Integer speedVal = pdc.get(CTRL_SPEED_KEY, PersistentDataType.INTEGER);
        if (speedVal != null) speed = Math.max(0, speedVal.intValue());

        return new StoredAugments(silk, speed);
    }

    private static void applyStoredAugments(ItemStack controllerItem, boolean silkTouch, int speedLevel) {
        if (!isControllerItem(controllerItem)) return;
        if (!controllerItem.hasItemMeta()) return;

        ItemMeta meta = controllerItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CTRL_SILK_KEY, PersistentDataType.INTEGER, silkTouch ? 1 : 0);
        pdc.set(CTRL_SPEED_KEY, PersistentDataType.INTEGER, Math.max(0, speedLevel));

        // Show stored augments in lore so players can see what's on the controller item.
        java.util.List<String> lore = meta.hasLore() && meta.getLore() != null
            ? new java.util.ArrayList<>(meta.getLore())
            : new java.util.ArrayList<>();

        // Remove any previous "Stored Augments" section (best-effort).
        lore.removeIf(line -> line != null && (line.contains("Stored Augments") || line.contains("Silk Touch") || line.contains("Speed")));

        if (silkTouch || speedLevel > 0) {
            lore.add(" ");
            lore.add("§bStored Augments:");
            lore.add(silkTouch ? "§7- §aSilk Touch" : "§7- §cSilk Touch");
            lore.add(speedLevel > 0 ? ("§7- §aSpeed " + speedLevel) : "§7- §cSpeed");
        }

        meta.setLore(lore);
        controllerItem.setItemMeta(meta);
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
