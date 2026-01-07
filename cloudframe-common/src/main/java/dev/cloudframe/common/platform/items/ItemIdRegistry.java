package dev.cloudframe.common.platform.items;

/**
 * Platform adapter for mapping between a platform item type and its stable string identifier.
 *
 * Identifiers should be stable across saves (e.g. "minecraft:stone").
 *
 * @param <ITEM> platform item type (e.g., Bukkit Material, Fabric Item)
 */
public interface ItemIdRegistry<ITEM> {

    /** Returns the stable id of the given item, or null if unavailable. */
    String idOf(ITEM item);

    /** Resolves an item by its stable id, or null if unknown/invalid. */
    ITEM itemById(String id);
}
