package dev.cloudframe.cloudframe.gui;

import org.bukkit.inventory.InventoryHolder;

import dev.cloudframe.cloudframe.quarry.Quarry;

public class QuarryHolder implements InventoryHolder {

    private final Quarry quarry;

    public QuarryHolder(Quarry quarry) {
        this.quarry = quarry;
    }

    public Quarry getQuarry() {
        return quarry;
    }

    @Override
    public org.bukkit.inventory.Inventory getInventory() {
        return null; // Not used
    }
}
