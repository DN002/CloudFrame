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

import dev.cloudframe.common.storage.Database;
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
        String worldName(Object loc);
        UUID worldId(Object loc);
        int blockX(Object loc);
        int blockY(Object loc);
        int blockZ(Object loc);
        Object worldByName(String name);
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

        for (int[] v : DIRS) {
            Object adj = locations.offset(loc, v[0], v[1], v[2]);
            PipeNode neighbor = pipes.get(adj);

            if (neighbor != null) {
                node.addNeighbor(neighbor);
                neighbor.addNeighbor(node);
                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("rebuildNeighbors", "Connected " + loc + " <-> " + adj);
                }
            }
        }
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

        debug.log("loadAll", "Finished loading pipes");
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
