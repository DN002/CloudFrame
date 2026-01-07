package dev.cloudframe.common.pipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import dev.cloudframe.common.storage.Database;
import dev.cloudframe.common.platform.world.WorldKeyAdapter;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugFlags;
import dev.cloudframe.common.util.DebugManager;

/**
 * Platform-agnostic pipe network manager.
 * Holds pipe nodes, rebuilds neighbors, pathfinds, discovers inventories,
 * and persists pipe locations. Platform-specific operations are provided via
 * ILocationAdapter and optional IPipeVisuals.
 */
public class PipeNetworkManager {

    private static final Debug debug = DebugManager.get(PipeNetworkManager.class);

    private final Map<Object, PipeNode> pipes = new HashMap<>();

    // Chunk index for spawning visuals on chunk load.
    private final Map<ChunkKey, Set<Object>> pipesByChunk = new HashMap<>();

    private IPipeVisuals visuals;
    private final ILocationAdapter locations;

    public record ChunkKey(UUID worldId, int cx, int cz) {}

    // 6-direction adjacency vectors
    public static final int[][] DIRS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    public interface ILocationAdapter {
        Object normalize(Object loc);
        Object offset(Object loc, int dx, int dy, int dz);
        ChunkKey chunkKey(Object loc);
        boolean isChunkLoaded(Object loc);
        boolean isInventoryAt(Object loc);
        Object worldOf(Object loc);
        WorldKeyAdapter<Object> worldKeyAdapter();
        default String worldName(Object loc) {
            WorldKeyAdapter<Object> adapter = worldKeyAdapter();
            if (adapter == null) return "";
            try {
                Object world = worldOf(loc);
                String key = adapter.key(world);
                return key != null ? key : "";
            } catch (Throwable ignored) {
                return "";
            }
        }
        UUID worldId(Object loc);
        int blockX(Object loc);
        int blockY(Object loc);
        int blockZ(Object loc);
        default Object worldByName(String name) {
            WorldKeyAdapter<Object> adapter = worldKeyAdapter();
            if (adapter == null) return null;
            try {
                return adapter.worldByKey(name);
            } catch (Throwable ignored) {
                return null;
            }
        }
        Object createLocation(Object world, int x, int y, int z);
    }

    public interface IPipeVisuals {
        void updatePipeAndNeighbors(Object loc);
        void shutdown();
    }

    public PipeNetworkManager(ILocationAdapter locations) {
        this.locations = locations;
    }

    public void setVisuals(IPipeVisuals visuals) {
        this.visuals = visuals;
    }

    public IPipeVisuals visualsManager() {
        return visuals;
    }

    public Collection<Object> pipeLocationsInChunk(Object chunkKeySource) {
        ChunkKey key = locations.chunkKey(chunkKeySource);
        Set<Object> set = pipesByChunk.get(key);
        if (set == null) return List.of();
        return List.copyOf(set);
    }

    public void addPipe(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("addPipe", "Adding pipe at " + loc);
        }

        PipeNode node = new PipeNode(loc);
        pipes.put(loc, node);

        indexAdd(loc);

        rebuildNeighbors(loc);

        if (visuals != null) {
            try {
                visuals.updatePipeAndNeighbors(loc);
            } catch (Exception ex) {
                debug.log("addPipe", "Exception updating pipe visuals at " + loc + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        } else if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("addPipe", "Pipe visuals manager is null; visuals disabled");
        }
    }

    public void removePipe(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("removePipe", "Removing pipe at " + loc);
        }

        pipes.remove(loc);
        indexRemove(loc);
        rebuildAll();

        if (visuals != null) {
            try {
                visuals.updatePipeAndNeighbors(loc);
            } catch (Exception ex) {
                debug.log("removePipe", "Exception updating pipe visuals at " + loc + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public PipeNode getPipe(Object loc) {
        return pipes.get(locations.normalize(loc));
    }

    public Collection<PipeNode> all() {
        return pipes.values();
    }

    private void rebuildNeighbors(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("rebuildNeighbors", "Rebuilding neighbors for " + loc);
        }

        PipeNode node = pipes.get(loc);
        if (node == null) {
            debug.log("rebuildNeighbors", "No pipe found at " + loc);
            return;
        }

        node.clearNeighbors();

        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            if (node.isInventorySideDisabled(dirIdx)) continue;

            int[] v = DIRS[dirIdx];
            Object adj = locations.offset(loc, v[0], v[1], v[2]);
            PipeNode neighbor = pipes.get(adj);

            if (neighbor != null) {
                int opposite = oppositeDirIndex(dirIdx);
                if (opposite >= 0 && neighbor.isInventorySideDisabled(opposite)) continue;
                node.addNeighbor(neighbor);
                neighbor.addNeighbor(node);
                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("rebuildNeighbors", "Connected " + loc + " <-> " + adj);
                }
            }
        }
    }

    private static int oppositeDirIndex(int dirIdx) {
        return switch (dirIdx) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            case 5 -> 4;
            default -> -1;
        };
    }

    public void rebuildAll() {
        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("rebuildAll", "Rebuilding all pipe neighbors (" + pipes.size() + " pipes)");
        }

        for (PipeNode node : pipes.values()) {
            node.clearNeighbors();
        }
        for (Object loc : pipes.keySet()) {
            rebuildNeighbors(loc);
        }
    }

    public List<PipeNode> findPath(PipeNode start, PipeNode end) {
        debug.log("findPath", "Finding path from " + start.getLocation() +
                " to " + end.getLocation());

        Queue<PipeNode> queue = new LinkedList<>();
        Map<PipeNode, PipeNode> parent = new HashMap<>();

        queue.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            PipeNode current = queue.poll();

            if (current == end) {
                List<PipeNode> path = buildPath(parent, end);
                debug.log("findPath", "Path found, length=" + path.size());
                return path;
            }

            for (PipeNode n : current.getNeighbors()) {
                if (!parent.containsKey(n)) {
                    parent.put(n, current);
                    queue.add(n);
                }
            }
        }

        debug.log("findPath", "No path found");
        return null;
    }

    private List<PipeNode> buildPath(Map<PipeNode, PipeNode> parent, PipeNode end) {
        List<PipeNode> path = new ArrayList<>();
        PipeNode cur = end;

        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }

        Collections.reverse(path);
        return path;
    }

    public List<Object> findInventoriesNear(PipeNode start) {
        debug.log("findInventoriesNear", "Searching inventories near " + start.getLocation());

        Set<Object> result = new HashSet<>();

        Queue<PipeNode> queue = new LinkedList<>();
        Set<PipeNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            PipeNode current = queue.poll();
            Object base = current.getLocation();

            for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
                if (current.isInventorySideDisabled(dirIdx)) continue;
                int[] v = DIRS[dirIdx];
                Object adj = locations.offset(base, v[0], v[1], v[2]);
                if (locations.isInventoryAt(adj)) {
                    debug.log("findInventoriesNear", "Found inventory at " + adj);
                    result.add(adj);
                }
            }

            for (PipeNode n : current.getNeighbors()) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }

        debug.log("findInventoriesNear", "Found " + result.size() + " inventories");
        return new ArrayList<>(result);
    }

    public List<PipeNode> getNeighborsOf(Object loc) {
        PipeNode node = pipes.get(locations.normalize(loc));
        if (node == null) {
            if (DebugFlags.STARTUP_LOAD_LOGGING) {
                debug.log("getNeighborsOf", "No pipe at " + loc);
            }
            return List.of();
        }
        return node.getNeighbors();
    }

    public void saveAll() {
        debug.log("saveAll", "Saving " + pipes.size() + " pipes to database");

        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM pipes");

            var ps = conn.prepareStatement(
                "INSERT INTO pipes (world, x, y, z, disabled_sides) VALUES (?, ?, ?, ?, ?)"
            );

            for (PipeNode node : pipes.values()) {
                Object loc = node.getLocation();

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("saveAll", "Saving pipe at " + loc);
                }

                ps.setString(1, locations.worldName(loc));
                ps.setInt(2, locations.blockX(loc));
                ps.setInt(3, locations.blockY(loc));
                ps.setInt(4, locations.blockZ(loc));
                ps.setInt(5, node.getDisabledInventorySides());

                ps.addBatch();
            }

            ps.executeBatch();
        });

        debug.log("saveAll", "Finished saving pipes");
    }

    public void loadAll() {
        debug.log("loadAll", "Loading pipes from database");

        pipes.clear();
        pipesByChunk.clear();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM pipes");

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                Object w = locations.worldByName(world);
                if (w == null) {
                    debug.log("loadAll", "World not found: " + world + " — skipping pipe");
                    continue;
                }

                Object loc = locations.createLocation(w, x, y, z);

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("loadAll", "Loaded pipe at " + loc);
                }

                addPipe(loc);

                try {
                    int disabledSides = rs.getInt("disabled_sides");
                    PipeNode node = pipes.get(locations.normalize(loc));
                    if (node != null) {
                        node.setDisabledInventorySides(disabledSides);
                    }
                } catch (java.sql.SQLException ignored) {
                    // Column may not exist in older DBs
                }
            }
        });

        // Ensure in-memory neighbors reflect persisted disabled sides.
        rebuildAll();

        debug.log("loadAll", "Finished loading pipes");
    }

    /**
     * Returns true when the given controller location is connected to at least one inventory,
     * either directly adjacent or via a pipe network.
     *
     * If the in-memory cache is missing pipe nodes (e.g., DB reset or older worlds), this can
     * lazily index currently-loaded pipe blocks in-world using {@code isPipeAt}.
     */
    public boolean hasValidOutputFrom(Object controllerLoc, Predicate<Object> isPipeAt, int maxScanPipes) {
        if (controllerLoc == null) return false;

        Object ctrl = locations.normalize(controllerLoc);

        // Direct adjacent inventory.
        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            int[] dir = DIRS[dirIdx];
            Object adj = locations.offset(ctrl, dir[0], dir[1], dir[2]);
            if (locations.isInventoryAt(adj)) return true;
        }

        if (isPipeAt == null) return false;

        // If we already have an adjacent cached pipe node, use the cached graph first.
        PipeNode start = null;
        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            int[] dir = DIRS[dirIdx];
            Object adj = locations.offset(ctrl, dir[0], dir[1], dir[2]);
            if (!isPipeAt.test(adj)) continue;

            PipeNode node = getPipe(adj);
            int towardControllerIdx = oppositeDirIndex(dirIdx);
            if (node != null && towardControllerIdx >= 0 && node.isInventorySideDisabled(towardControllerIdx)) {
                continue;
            }
            if (node != null) {
                start = node;
                break;
            }
        }

        if (start != null) {
            List<Object> inventories = findInventoriesNear(start);
            if (inventories != null && !inventories.isEmpty()) return true;
        }

        // Lazy indexing: BFS through currently-loaded pipe blocks and ensure they exist in the cache.
        int limit = maxScanPipes <= 0 ? 8192 : maxScanPipes;
        java.util.ArrayDeque<Object> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<Object> visited = new java.util.HashSet<>();

        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            int[] dir = DIRS[dirIdx];
            Object adj = locations.offset(ctrl, dir[0], dir[1], dir[2]);
            if (!isPipeAt.test(adj)) continue;

            PipeNode node = getPipe(adj);
            int towardControllerIdx = oppositeDirIndex(dirIdx);
            if (node != null && towardControllerIdx >= 0 && node.isInventorySideDisabled(towardControllerIdx)) {
                continue;
            }
            queue.add(locations.normalize(adj));
        }

        boolean foundInventory = false;

        while (!queue.isEmpty() && visited.size() < limit) {
            Object pos = queue.pollFirst();
            if (pos == null) continue;
            pos = locations.normalize(pos);
            if (!visited.add(pos)) continue;

            if (getPipe(pos) == null) {
                addPipe(pos);
            }

            PipeNode node = getPipe(pos);

            for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
                int[] dir = DIRS[dirIdx];
                Object adj = locations.offset(pos, dir[0], dir[1], dir[2]);

                if (isPipeAt.test(adj)) {
                    Object normAdj = locations.normalize(adj);
                    if (!visited.contains(normAdj)) {
                        queue.add(normAdj);
                    }
                }

                boolean sideEnabled = (node == null) || !node.isInventorySideDisabled(dirIdx);
                if (sideEnabled && locations.isInventoryAt(adj)) {
                    foundInventory = true;
                }
            }
        }

        if (foundInventory) return true;

        // Re-check with cached graph now that we've added missing pipes.
        PipeNode start2 = null;
        for (int dirIdx = 0; dirIdx < DIRS.length; dirIdx++) {
            int[] dir = DIRS[dirIdx];
            Object adj = locations.offset(ctrl, dir[0], dir[1], dir[2]);
            if (!isPipeAt.test(adj)) continue;

            PipeNode node = getPipe(adj);
            int towardControllerIdx = oppositeDirIndex(dirIdx);
            if (node != null && towardControllerIdx >= 0 && node.isInventorySideDisabled(towardControllerIdx)) {
                continue;
            }
            if (node != null) {
                start2 = node;
                break;
            }
        }

        if (start2 == null) return false;
        List<Object> inventories = findInventoriesNear(start2);
        return inventories != null && !inventories.isEmpty();
    }

    private void indexAdd(Object loc) {
        ChunkKey key = locations.chunkKey(loc);
        pipesByChunk.computeIfAbsent(key, k -> new HashSet<>()).add(loc);
    }

    private void indexRemove(Object loc) {
        ChunkKey key = locations.chunkKey(loc);
        Set<Object> set = pipesByChunk.get(key);
        if (set == null) return;
        set.remove(loc);
        if (set.isEmpty()) pipesByChunk.remove(key);
    }
}
