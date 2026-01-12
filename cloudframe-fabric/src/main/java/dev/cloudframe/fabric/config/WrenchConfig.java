package dev.cloudframe.fabric.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.util.Identifier;

/**
 * Server-owner editable config for wrench behavior.
 */
public final class WrenchConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * If non-empty, only these blocks are eligible for rotation.
     * Use full IDs like "minecraft:furnace" or "cloudframe:trash_can".
     */
    public List<String> rotationAllowlist = List.of();

    /**
     * Blocks that can never be rotated by the wrench.
     * Use full IDs like "minecraft:chest".
     */
    public List<String> rotationDenylist = defaultRotationDenylist();

    public static WrenchConfig loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    WrenchConfig cfg = GSON.fromJson(r, WrenchConfig.class);
                    if (cfg == null) cfg = new WrenchConfig();
                    if (cfg.rotationAllowlist == null) cfg.rotationAllowlist = List.of();
                    if (cfg.rotationDenylist == null) cfg.rotationDenylist = defaultRotationDenylist();
                    // If missing, default to false.
                    return cfg;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to defaults + rewrite.
        }

        WrenchConfig cfg = new WrenchConfig();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, w);
            }
        } catch (Throwable ignored) {
        }
        return cfg;
    }

    public boolean isRotationAllowed(Identifier blockId) {
        if (blockId == null) return false;
        String id = blockId.toString();

        if (rotationAllowlist != null && !rotationAllowlist.isEmpty()) {
            if (!rotationAllowlist.contains(id)) return false;
        }

        if (rotationDenylist != null && rotationDenylist.contains(id)) {
            return false;
        }

        return true;
    }

    public Set<Identifier> parsedRotationAllowlist() {
        return parse(rotationAllowlist);
    }

    public Set<Identifier> parsedRotationDenylist() {
        return parse(rotationDenylist);
    }

    private static Set<Identifier> parse(List<String> ids) {
        Set<Identifier> out = new LinkedHashSet<>();
        if (ids == null) return out;
        for (String s : ids) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(Identifier.of(s.trim()));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static List<String> defaultRotationDenylist() {
        // Conservative defaults: blocks that are multi-block, adjacency-sensitive, or commonly griefy.
        return List.of(
            "minecraft:bed",
            "minecraft:oak_bed",
            "minecraft:spruce_bed",
            "minecraft:birch_bed",
            "minecraft:jungle_bed",
            "minecraft:acacia_bed",
            "minecraft:cherry_bed",
            "minecraft:dark_oak_bed",
            "minecraft:mangrove_bed",
            "minecraft:bamboo_bed",
            "minecraft:crimson_bed",
            "minecraft:warped_bed",
            // Rails are shape+neighbor driven and don't rotate predictably.
            "minecraft:rail",
            "minecraft:powered_rail",
            "minecraft:detector_rail",
            "minecraft:activator_rail"
        );
    }
}
