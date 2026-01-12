package dev.cloudframe.fabric.config;

import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFlags;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-owner editable config stored alongside CloudFrame runtime files
 * (debug.log, cloudframe.db) under config/cloudframe/.
 *
 * Format:
 * - key=value (for booleans and simple values)
 * - key:      (for lists)
 *     - value
 *     - value
 *
 * Comments start with '#'.
 */
public final class CloudFrameConfigFile {

    public record Loaded(WrenchConfig wrenchConfig) {}

    private CloudFrameConfigFile() {}

    public static Loaded loadOrCreate(Path file, Debug debug) {
        if (debug != null) debug.log("config", "Loading config: " + file);

        if (!Files.exists(file)) {
            // Try migration from older JSON files if present.
            Path dir = file.getParent();
            Path oldWrenchJson = dir.resolve("wrench.json");

            WrenchConfig wrench = null;

            try {
                if (Files.exists(oldWrenchJson)) {
                    wrench = WrenchConfig.loadOrCreate(oldWrenchJson);
                    if (debug != null) debug.log("config", "Migrating wrench.json -> config.txt");
                }
            } catch (Throwable t) {
                if (debug != null) debug.log("config", "Failed reading wrench.json for migration: " + t);
            }

            if (wrench == null) wrench = new WrenchConfig();

            writeDefaultFile(file, wrench);
        }

        WrenchConfig wrench = new WrenchConfig();

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Map<String, String> kv = parseKeyValues(lines);

            // Wrench rotation
            wrench.rotationAllowlist = parseList(lines, kv, "rotation.allowlist");
            List<String> deny = parseList(lines, kv, "rotation.denylist");
            wrench.rotationDenylist = deny.isEmpty() ? WrenchConfig.defaultRotationDenylist() : deny;

            // Debug flags
            DebugFlags.TICK_LOGGING = parseBool(kv.get("debug.tickLogging"), DebugFlags.TICK_LOGGING);
            DebugFlags.PIPE_PARTICLES = parseBool(kv.get("debug.pipeParticles"), DebugFlags.PIPE_PARTICLES);
            DebugFlags.CHUNK_LOGGING = parseBool(kv.get("debug.chunkLogging"), DebugFlags.CHUNK_LOGGING);
            DebugFlags.STARTUP_LOAD_LOGGING = parseBool(kv.get("debug.startupLoadLogging"), DebugFlags.STARTUP_LOAD_LOGGING);
            DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING = parseBool(kv.get("debug.resourcePackVerboseLogging"), DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING);
            DebugFlags.VISUAL_SPAWN_LOGGING = parseBool(kv.get("debug.visualSpawnLogging"), DebugFlags.VISUAL_SPAWN_LOGGING);
            DebugFlags.PICKBLOCK_LOGGING = parseBool(kv.get("debug.pickblockLogging"), DebugFlags.PICKBLOCK_LOGGING);
            DebugFlags.WRENCH_USE_LOGGING = parseBool(kv.get("debug.wrenchUseLogging"), DebugFlags.WRENCH_USE_LOGGING);

            if (debug != null) {
                debug.log("config", "Loaded rotation.allowlist size=" + wrench.rotationAllowlist.size());
                debug.log("config", "Loaded rotation.denylist size=" + wrench.rotationDenylist.size());
                debug.log("config", "DebugFlags.WRENCH_USE_LOGGING=" + DebugFlags.WRENCH_USE_LOGGING);
            }

        } catch (Throwable t) {
            if (debug != null) debug.log("config", "Failed reading config.txt: " + t);
        }

        return new Loaded(wrench);
    }

    private static void writeDefaultFile(Path file, WrenchConfig wrench) {
        try {
            Files.createDirectories(file.getParent());

            List<String> out = new ArrayList<>();
            out.add("# CloudFrame server config");
            out.add("# Location: " + file.toAbsolutePath());
            out.add("# Format: key=value  (comments start with #)");
            out.add("#");
            out.add("# Wrench rotation settings");
            out.add("# rotation.allowlist: if non-empty, ONLY these blocks rotate");
            out.add("rotation.allowlist:");
            out.add("#   - minecraft:furnace");
            out.add("#   - cloudframe:trash_can");
            for (String s : safeList(wrench.rotationAllowlist)) {
                if (s == null || s.isBlank()) continue;
                out.add("  - " + s.trim());
            }
            out.add("# rotation.denylist: blocks that NEVER rotate");
            out.add("rotation.denylist:");
            for (String s : safeList(wrench.rotationDenylist)) {
                if (s == null || s.isBlank()) continue;
                out.add("  - " + s.trim());
            }
            out.add("#");
            out.add("# Debug flags (affects CloudFrame debug.log, not server console)");
            out.add("debug.tickLogging=" + DebugFlags.TICK_LOGGING);
            out.add("debug.pipeParticles=" + DebugFlags.PIPE_PARTICLES);
            out.add("debug.chunkLogging=" + DebugFlags.CHUNK_LOGGING);
            out.add("debug.startupLoadLogging=" + DebugFlags.STARTUP_LOAD_LOGGING);
            out.add("debug.resourcePackVerboseLogging=" + DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING);
            out.add("debug.visualSpawnLogging=" + DebugFlags.VISUAL_SPAWN_LOGGING);
            out.add("debug.pickblockLogging=" + DebugFlags.PICKBLOCK_LOGGING);
            out.add("# Very spammy per-click logs; keep false unless diagnosing");
            out.add("debug.wrenchUseLogging=" + DebugFlags.WRENCH_USE_LOGGING);

            Files.write(file, out, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static Map<String, String> parseKeyValues(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;

            int eq = line.indexOf('=');
            if (eq <= 0) continue;

            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (!key.isEmpty()) out.put(key, val);
        }
        return out;
    }

    private static List<String> parseCsvList(String value) {
        if (value == null) return List.of();
        String v = value.trim();
        if (v.isEmpty()) return List.of();

        return Arrays.stream(v.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static List<String> parseList(List<String> lines, Map<String, String> kv, String key) {
        // Preferred: vertical list section "key:".
        List<String> section = parseListSection(lines, key);
        if (!section.isEmpty()) return section;

        // Back-compat: comma-separated single-line key=value.
        return parseCsvList(kv.get(key));
    }

    private static List<String> parseListSection(List<String> lines, String key) {
        if (lines == null) return List.of();

        String header = key + ":";
        boolean inSection = false;
        List<String> out = new ArrayList<>();

        for (String raw : lines) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (!inSection) {
                if (trimmed.equalsIgnoreCase(header)) {
                    inSection = true;
                }
                continue;
            }

            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) continue;

            // Next section or next key=value starts -> stop.
            if (trimmed.endsWith(":") && !trimmed.startsWith("-")) break;
            if (trimmed.contains("=") && !trimmed.startsWith("-")) break;

            if (trimmed.startsWith("-")) {
                String val = trimmed.substring(1).trim();
                if (!val.isEmpty()) out.add(val);
            }
        }

        return out;
    }

    private static boolean parseBool(String value, boolean def) {
        if (value == null) return def;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> def;
        };
    }

    private static List<String> safeList(List<String> in) {
        return in == null ? List.of() : in;
    }
}
