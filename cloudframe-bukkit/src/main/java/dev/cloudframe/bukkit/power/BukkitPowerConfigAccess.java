package dev.cloudframe.bukkit.power;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Prototype Bukkit power implementation backed by vanilla Materials.
 */
public final class BukkitPowerConfigAccess implements BukkitPowerAccess {

    private final Material cable;
    private final Material stratusPanel;
    private final Material cloudTurbine;
    private final Material cloudCell;

    private final long cellCapacityCfe;
    private final BukkitPowerCellRepository cells;

    public BukkitPowerConfigAccess(Material cable, Material stratusPanel, Material cloudTurbine, Material cloudCell, long cellCapacityCfe, BukkitPowerCellRepository cells) {
        this.cable = cable;
        this.stratusPanel = stratusPanel;
        this.cloudTurbine = cloudTurbine;
        this.cloudCell = cloudCell;
        this.cellCapacityCfe = Math.max(0L, cellCapacityCfe);
        this.cells = cells;
    }

    public static Material materialOrNull(String name) {
        if (name == null || name.isBlank()) return null;
        Material m = Material.matchMaterial(name.trim());
        return m;
    }

    public static BukkitPowerConfigAccess fromConfig(ConfigurationSection powerSection, BukkitPowerCellRepository repo) {
        Material cable = materialOrNull(powerSection == null ? null : powerSection.getString("blocks.cable"));
        Material panel = materialOrNull(powerSection == null ? null : powerSection.getString("blocks.stratus_panel"));
        Material turbine = materialOrNull(powerSection == null ? null : powerSection.getString("blocks.cloud_turbine"));
        Material cell = materialOrNull(powerSection == null ? null : powerSection.getString("blocks.cloud_cell"));

        long cap = powerSection != null ? powerSection.getLong("cell_capacity_cfe", 1_000_000L) : 1_000_000L;

        // Fail-closed: if any mapping is missing, keep power inert.
        if (cable == null || panel == null || turbine == null || cell == null) {
            return new BukkitPowerConfigAccess(null, null, null, null, cap, repo);
        }

        return new BukkitPowerConfigAccess(cable, panel, turbine, cell, cap, repo);
    }

    private Material typeAt(Location loc) {
        if (loc == null) return null;
        World w = loc.getWorld();
        if (w == null) return null;
        return w.getBlockAt(loc).getType();
    }

    @Override
    public boolean isCable(Location loc) {
        if (cable == null) return false;
        return typeAt(loc) == cable;
    }

    @Override
    public boolean isProducer(Location loc) {
        if (loc == null) return false;
        Material t = typeAt(loc);
        return (stratusPanel != null && t == stratusPanel) || (cloudTurbine != null && t == cloudTurbine);
    }

    @Override
    public long producerCfePerTick(Location producerLoc) {
        if (producerLoc == null) return 0L;
        Material t = typeAt(producerLoc);
        if (t == null) return 0L;

        if (stratusPanel != null && t == stratusPanel) {
            return measureStratusPanelCfePerTick(producerLoc);
        }
        if (cloudTurbine != null && t == cloudTurbine) {
            return 32L;
        }
        return 0L;
    }

    private long measureStratusPanelCfePerTick(Location loc) {
        World w = loc.getWorld();
        if (w == null) return 0L;

        Block above = w.getBlockAt(loc).getRelative(BlockFace.UP);
        int sky = 0;
        try {
            sky = above.getLightFromSky();
        } catch (Throwable ignored) {
            // Best effort.
            sky = 0;
        }

        if (sky < 0) sky = 0;
        if (sky > 15) sky = 15;

        int gen = (8 * sky) / 15;
        if (gen <= 0) return 0L;

        try {
            if (w.isThundering()) {
                gen = gen / 2;
            } else if (w.hasStorm()) {
                gen = (gen * 3) / 4;
            }
        } catch (Throwable ignored) {
        }

        return Math.max(0L, (long) gen);
    }

    @Override
    public boolean isCell(Location loc) {
        if (cloudCell == null) return false;
        return typeAt(loc) == cloudCell;
    }

    @Override
    public long cellInsertCfe(Location cellLoc, long amount) {
        if (amount <= 0L) return 0L;
        if (!isCell(cellLoc)) return 0L;
        if (cells == null) return 0L;

        long stored = cells.getStoredCfe(cellLoc);
        long cap = cellCapacityCfe;
        if (cap <= 0L) return 0L;

        long space = Math.max(0L, cap - stored);
        long inserted = Math.min(amount, space);
        if (inserted <= 0L) return 0L;

        cells.setStoredCfe(cellLoc, stored + inserted);
        return inserted;
    }

    @Override
    public long cellExtractCfe(Location cellLoc, long amount) {
        if (amount <= 0L) return 0L;
        if (!isCell(cellLoc)) return 0L;
        if (cells == null) return 0L;

        long stored = cells.getStoredCfe(cellLoc);
        long extracted = Math.min(amount, Math.max(0L, stored));
        if (extracted <= 0L) return 0L;

        cells.setStoredCfe(cellLoc, stored - extracted);
        return extracted;
    }

    @Override
    public long cellStoredCfe(Location cellLoc) {
        if (!isCell(cellLoc)) return 0L;
        if (cells == null) return 0L;
        return cells.getStoredCfe(cellLoc);
    }
}
