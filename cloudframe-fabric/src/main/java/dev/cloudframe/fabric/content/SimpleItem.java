package dev.cloudframe.fabric.content;

import net.minecraft.item.Item;

/**
 * Simple Item subclass that defers registry key validation.
 * This allows creation without Item.Settings requiring a registry context.
 */
public class SimpleItem extends Item {
    public SimpleItem() {
        // Pass null settings to avoid validation
        super(new Item.Settings());
    }
}
