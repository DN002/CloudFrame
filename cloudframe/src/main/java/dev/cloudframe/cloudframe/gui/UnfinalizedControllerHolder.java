package dev.cloudframe.cloudframe.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class UnfinalizedControllerHolder implements InventoryHolder {

    private final Location controllerLoc;

    public UnfinalizedControllerHolder(Location controllerLoc) {
        this.controllerLoc = controllerLoc;
    }

    public Location getControllerLoc() {
        return controllerLoc;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
