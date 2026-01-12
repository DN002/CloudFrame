package dev.cloudframe.fabric.quarry;

import dev.cloudframe.common.quarry.augments.CloudFrameQuarryAugmentResolver;
import dev.cloudframe.common.quarry.augments.QuarryAugments;
import dev.cloudframe.fabric.content.AugmentBooks;
import dev.cloudframe.fabric.platform.items.FabricItemIdRegistry;
import dev.cloudframe.fabric.pipes.FabricItemStackAdapter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Fabric-side bridge that resolves augments using shared (id-based) rules,
 * with a fallback for legacy enchanted-book augments.
 */
public final class FabricQuarryAugmentResolver {

    private static final CloudFrameQuarryAugmentResolver<ItemStack, Item> ID_RESOLVER =
        new CloudFrameQuarryAugmentResolver<>(
            FabricItemStackAdapter.INSTANCE,
            ItemStack::getItem,
            FabricItemIdRegistry.INSTANCE
        );

    private FabricQuarryAugmentResolver() {
    }

    public static QuarryAugments resolve(ItemStack stack) {
        QuarryAugments byId = ID_RESOLVER.resolve(stack);
        if (byId != null) return byId;

        if (stack == null || stack.isEmpty()) return null;

        boolean silk = AugmentBooks.isSilkTouch(stack);
        int speed = AugmentBooks.speedTier(stack);
        int fortune = AugmentBooks.fortuneTier(stack);

        if (!silk && speed <= 0 && fortune <= 0) return null;
        return new QuarryAugments(silk, speed, fortune);
    }
}
