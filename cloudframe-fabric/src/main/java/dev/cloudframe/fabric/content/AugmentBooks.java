package dev.cloudframe.fabric.content;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * Creates and identifies augment items as vanilla enchanted books.
 */
public final class AugmentBooks {

    private AugmentBooks() {
    }

    private static final String NBT_ROOT = "CloudFrameAugment";
    private static final String NBT_KIND = "Kind";
    private static final String NBT_TIER = "Tier";

    private static void writeRoot(ItemStack stack, NbtCompound root) {
        NbtCompound data = new NbtCompound();
        data.put(NBT_ROOT, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    private static NbtCompound readRoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        NbtCompound nbt = data.copyNbt();
        return nbt.getCompound(NBT_ROOT).orElse(null);
    }

    public static ItemStack silkTouch() {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        NbtCompound root = new NbtCompound();
        root.putString(NBT_KIND, "silk");
        writeRoot(stack, root);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Silk Touch Augment"));
        return stack;
    }

    public static ItemStack speed(int tier) {
        int t = Math.max(1, Math.min(3, tier));
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        NbtCompound root = new NbtCompound();
        root.putString(NBT_KIND, "speed");
        root.putInt(NBT_TIER, t);
        writeRoot(stack, root);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Speed Augment (Tier " + t + ")"));
        return stack;
    }

    public static boolean isSilkTouch(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.ENCHANTED_BOOK)) return false;
        NbtCompound root = readRoot(stack);
        if (root == null) return false;
        return "silk".equals(root.getString(NBT_KIND).orElse(""));
    }

    /**
     * @return speed tier 1..3, or 0 if not a speed augment book.
     */
    public static int speedTier(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.ENCHANTED_BOOK)) return 0;
        NbtCompound root = readRoot(stack);
        if (root == null) return 0;
        if (!"speed".equals(root.getString(NBT_KIND).orElse(""))) return 0;
        int tier = root.getInt(NBT_TIER).orElse(0);
        return Math.max(1, Math.min(3, tier));
    }
}
