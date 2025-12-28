package dev.cloudframe.cloudframe.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.Material;

import dev.cloudframe.cloudframe.CloudFrame;
import dev.cloudframe.cloudframe.items.MarkerTool;
import dev.cloudframe.cloudframe.items.WrenchTool;
import dev.cloudframe.cloudframe.items.TubeItem;
import dev.cloudframe.cloudframe.items.QuarryControllerBlock;

/**
 * Registers simple crafting recipes for CloudFrame special items.
 */
public class RecipeManager {

    public static void register(CloudFrame plugin) {
        // Marker Tool: shape
        ItemStack marker = MarkerTool.create();
        NamespacedKey keyMarker = new NamespacedKey(plugin, "cloud_marker");
        ShapedRecipe rMarker = new ShapedRecipe(keyMarker, marker);
        rMarker.shape(" B ", " L ", "   ");
        rMarker.setIngredient('B', Material.BLAZE_ROD);
        rMarker.setIngredient('L', Material.LAPIS_LAZULI);
        plugin.getServer().addRecipe(rMarker);

        // Wrench: shape
        ItemStack wrench = WrenchTool.create();
        NamespacedKey keyWrench = new NamespacedKey(plugin, "cloud_wrench");
        ShapedRecipe rWrench = new ShapedRecipe(keyWrench, wrench);
        rWrench.shape(" I ", " R ", "   ");
        rWrench.setIngredient('I', Material.IRON_HOE);
        rWrench.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(rWrench);

        // Tube item: shape
        ItemStack tube = TubeItem.create();
        NamespacedKey keyTube = new NamespacedKey(plugin, "cloud_tube");
        ShapedRecipe rTube = new ShapedRecipe(keyTube, tube);
        rTube.shape(" C ", " B ", "   ");
        rTube.setIngredient('C', Material.COPPER_BLOCK);
        rTube.setIngredient('B', Material.BLUE_DYE);
        plugin.getServer().addRecipe(rTube);

        // Quarry controller: shape
        ItemStack controller = QuarryControllerBlock.create();
        NamespacedKey keyController = new NamespacedKey(plugin, "quarry_controller_item");
        ShapedRecipe rController = new ShapedRecipe(keyController, controller);
        rController.shape(" C ", " G ", "   ");
        rController.setIngredient('C', Material.COPPER_BLOCK);
        rController.setIngredient('G', Material.GOLD_INGOT);
        plugin.getServer().addRecipe(rController);

        DebugManager.get(RecipeManager.class).log("register", "Registered CloudFrame crafting recipes");
    }
}
