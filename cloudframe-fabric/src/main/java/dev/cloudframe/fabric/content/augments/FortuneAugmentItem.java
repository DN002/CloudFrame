package dev.cloudframe.fabric.content.augments;

import net.minecraft.item.Item;

public class FortuneAugmentItem extends Item {

    private final int tier;

    public FortuneAugmentItem(int tier, Settings settings) {
        super(settings);
        this.tier = Math.max(1, tier);
    }

    public int tier() {
        return tier;
    }
}
