package dev.cloudframe.cloudframe.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;

public class InventoryUtil {

    public static boolean isInventory(Block block) {
        BlockState state = block.getState();
        return state instanceof InventoryHolder;
    }

    public static InventoryHolder getInventory(Block block) {
        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder) {
            return holder;
        }
        return null;
    }
}
