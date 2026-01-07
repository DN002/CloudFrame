package dev.cloudframe.fabric.platform.items;

import dev.cloudframe.common.platform.items.ItemIdRegistry;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Fabric implementation of {@link ItemIdRegistry}.
 */
public final class FabricItemIdRegistry implements ItemIdRegistry<Item> {

    public static final FabricItemIdRegistry INSTANCE = new FabricItemIdRegistry();

    private FabricItemIdRegistry() {
    }

    @Override
    public String idOf(Item item) {
        if (item == null) return null;
        try {
            Identifier id = Registries.ITEM.getId(item);
            return id == null ? null : id.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public Item itemById(String id) {
        if (id == null) return null;
        String s = id.trim();
        if (s.isEmpty()) return null;
        try {
            Identifier ident = Identifier.of(s);
            if (!Registries.ITEM.containsId(ident)) return null;
            return Registries.ITEM.get(ident);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
