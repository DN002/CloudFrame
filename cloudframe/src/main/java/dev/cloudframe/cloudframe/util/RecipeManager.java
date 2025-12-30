package dev.cloudframe.cloudframe.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.Material;

import dev.cloudframe.cloudframe.CloudFrame;
import dev.cloudframe.cloudframe.items.MarkerTool;
import dev.cloudframe.cloudframe.items.WrenchTool;
import dev.cloudframe.cloudframe.items.TubeItem;
import dev.cloudframe.cloudframe.items.QuarryControllerBlock;
import dev.cloudframe.cloudframe.items.SilkTouchAugment;
import dev.cloudframe.cloudframe.items.SpeedAugment;

/**
 * Registers simple crafting recipes for CloudFrame special items.
 */
public class RecipeManager {

    public static void register(CloudFrame plugin) {
        // Clear any existing recipes with these keys
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "cloud_marker"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "cloud_wrench"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "cloud_tube"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "quarry_controller_item"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "silk_touch_augment"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "speed_augment_1"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "speed_augment_2"));
        plugin.getServer().removeRecipe(new NamespacedKey(plugin, "speed_augment_3"));

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

        // Silk Touch augment: simple recipe
        ItemStack silk = SilkTouchAugment.create();
        NamespacedKey keySilk = new NamespacedKey(plugin, "silk_touch_augment");
        ShapedRecipe rSilk = new ShapedRecipe(keySilk, silk);
        rSilk.shape(" E ", " B ", "   ");
        rSilk.setIngredient('E', Material.EMERALD);
        rSilk.setIngredient('B', Material.BOOK);
        plugin.getServer().addRecipe(rSilk);

        // Speed augment tiers: I is base, II/III upgrade from previous.
        ItemStack speed1 = SpeedAugment.create(1);
        NamespacedKey keySpeed1 = new NamespacedKey(plugin, "speed_augment_1");
        ShapedRecipe rSpeed1 = new ShapedRecipe(keySpeed1, speed1);
        rSpeed1.shape(" S ", " R ", "   ");
        rSpeed1.setIngredient('S', Material.SUGAR);
        rSpeed1.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(rSpeed1);

        ItemStack speed2 = SpeedAugment.create(2);
        NamespacedKey keySpeed2 = new NamespacedKey(plugin, "speed_augment_2");
        ShapedRecipe rSpeed2 = new ShapedRecipe(keySpeed2, speed2);
        rSpeed2.shape(" G ", " A ", "   ");
        rSpeed2.setIngredient('G', Material.GOLD_INGOT);
        rSpeed2.setIngredient('A', new RecipeChoice.ExactChoice(speed1));
        plugin.getServer().addRecipe(rSpeed2);

        ItemStack speed3 = SpeedAugment.create(3);
        NamespacedKey keySpeed3 = new NamespacedKey(plugin, "speed_augment_3");
        ShapedRecipe rSpeed3 = new ShapedRecipe(keySpeed3, speed3);
        rSpeed3.shape(" D ", " A ", "   ");
        rSpeed3.setIngredient('D', Material.DIAMOND);
        rSpeed3.setIngredient('A', new RecipeChoice.ExactChoice(speed2));
        plugin.getServer().addRecipe(rSpeed3);

        DebugManager.get(RecipeManager.class).log("register", "Registered CloudFrame crafting recipes");
    }
}
