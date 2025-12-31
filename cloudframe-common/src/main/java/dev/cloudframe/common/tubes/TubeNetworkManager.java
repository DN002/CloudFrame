package dev.cloudframe.common.tubes;

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
 * Platform-agnostic tube network manager.
 * Holds tube nodes, rebuilds neighbors, pathfinds, discovers inventories,
 * and persists tube locations. Platform-specific operations are provided via
 * ILocationAdapter and optional ITubeVisuals.
 */
public class TubeNetworkManager {

    private static final Debug debug = DebugManager.get(TubeNetworkManager.class);

    private final Map<Object, TubeNode> tubes = new HashMap<>();

    // Chunk index for spawning visuals on chunk load.
    private final Map<ChunkKey, Set<Object>> tubesByChunk = new HashMap<>();

    private ITubeVisuals visuals;
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

    /**
     * Adapter for platform-specific location/world/block operations.
     */
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

    /**
     * Optional visuals interface (entity-only displays, etc.).
     */
    public interface ITubeVisuals {
        void updateTubeAndNeighbors(Object loc);
        void shutdown();
    }

    public TubeNetworkManager(ILocationAdapter locations) {
        this.locations = locations;
    }

    public void setVisuals(ITubeVisuals visuals) {
        this.visuals = visuals;
    }

    public ITubeVisuals visualsManager() {
        return visuals;
    }

    public Collection<Object> tubeLocationsInChunk(Object chunkKeySource) {
        ChunkKey key = locations.chunkKey(chunkKeySource);
        Set<Object> set = tubesByChunk.get(key);
        if (set == null) return List.of();
        return List.copyOf(set);
    }

    // =========================
    //  Tube Registration
    // =========================

    public void addTube(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("addTube", "Adding tube at " + loc);
        }

        TubeNode node = new TubeNode(loc);
        tubes.put(loc, node);

        indexAdd(loc);

        rebuildNeighbors(loc);

        if (visuals != null) {
            try {
                visuals.updateTubeAndNeighbors(loc);
            } catch (Exception ex) {
                debug.log("addTube", "Exception updating tube visuals at " + loc + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        } else if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("addTube", "Tube visuals manager is null; visuals disabled");
        }
    }

    public void removeTube(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("removeTube", "Removing tube at " + loc);
        }

        tubes.remove(loc);
        indexRemove(loc);
        rebuildAll();

        if (visuals != null) {
            try {
                visuals.updateTubeAndNeighbors(loc);
            } catch (Exception ex) {
                debug.log("removeTube", "Exception updating tube visuals at " + loc + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public TubeNode getTube(Object loc) {
        return tubes.get(locations.normalize(loc));
    }

    public Collection<TubeNode> all() {
        return tubes.values();
    }

    // =========================
    //  Neighbor Rebuilding
    // =========================

    private void rebuildNeighbors(Object loc) {
        loc = locations.normalize(loc);

        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("rebuildNeighbors", "Rebuilding neighbors for " + loc);
        }

        TubeNode node = tubes.get(loc);
        if (node == null) {
            debug.log("rebuildNeighbors", "No tube found at " + loc);
            return;
        }

        node.clearNeighbors();

        for (int[] v : DIRS) {
            Object adj = locations.offset(loc, v[0], v[1], v[2]);
            TubeNode neighbor = tubes.get(adj);

            if (neighbor != null) {
                node.addNeighbor(neighbor);
                neighbor.addNeighbor(node);
                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("rebuildNeighbors", "Connected " + loc + " <-> " + adj);
                }
            }
        }
    }

    private void rebuildAll() {
        if (DebugFlags.STARTUP_LOAD_LOGGING) {
            debug.log("rebuildAll", "Rebuilding all tube neighbors (" + tubes.size() + " tubes)");
        }

        for (TubeNode node : tubes.values()) {
            node.clearNeighbors();
        }
        for (Object loc : tubes.keySet()) {
            rebuildNeighbors(loc);
        }
    }

    // =========================
    //  BFS Pathfinding
    // =========================

    public List<TubeNode> findPath(TubeNode start, TubeNode end) {
        debug.log("findPath", "Finding path from " + start.getLocation() +
                " to " + end.getLocation());

        Queue<TubeNode> queue = new LinkedList<>();
        Map<TubeNode, TubeNode> parent = new HashMap<>();

        queue.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            TubeNode current = queue.poll();

            if (current == end) {
                List<TubeNode> path = buildPath(parent, end);
                debug.log("findPath", "Path found, length=" + path.size());
                return path;
            }

            for (TubeNode n : current.getNeighbors()) {
                if (!parent.containsKey(n)) {
                    parent.put(n, current);
                    queue.add(n);
                }
            }
        }

        debug.log("findPath", "No path found");
        return null;
    }

    private List<TubeNode> buildPath(Map<TubeNode, TubeNode> parent, TubeNode end) {
        List<TubeNode> path = new ArrayList<>();
        TubeNode cur = end;

        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }

        Collections.reverse(path);
        return path;
    }

    // =========================
    //  Inventory Discovery
    // =========================

    public List<Object> findInventoriesNear(TubeNode start) {
        debug.log("findInventoriesNear", "Searching inventories near " + start.getLocation());

        Set<Object> result = new HashSet<>();

        Queue<TubeNode> queue = new LinkedList<>();
        Set<TubeNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            TubeNode current = queue.poll();
            Object base = current.getLocation();

            // Check all 6 adjacent blocks for inventories
            for (int[] v : DIRS) {
                Object adj = locations.offset(base, v[0], v[1], v[2]);
                if (locations.isInventoryAt(adj)) {
                    debug.log("findInventoriesNear", "Found inventory at " + adj);
                    result.add(adj);
                }
            }

            // Continue BFS through tubes
            for (TubeNode n : current.getNeighbors()) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }

        debug.log("findInventoriesNear", "Found " + result.size() + " inventories");
        return new ArrayList<>(result);
    }

    public List<TubeNode> getNeighborsOf(Object loc) {
        TubeNode node = tubes.get(locations.normalize(loc));
        if (node == null) {
            if (DebugFlags.STARTUP_LOAD_LOGGING) {
                debug.log("getNeighborsOf", "No tube at " + loc);
            }
            return List.of();
        }
        return node.getNeighbors();
    }

    // =========================
    //  Persistence
    // =========================

    public void saveAll() {
        debug.log("saveAll", "Saving " + tubes.size() + " tubes to database");

        Database.run(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM tubes");

            var ps = conn.prepareStatement(
                "INSERT INTO tubes (world, x, y, z) VALUES (?, ?, ?, ?)"
            );

            for (TubeNode node : tubes.values()) {
                Object loc = node.getLocation();

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("saveAll", "Saving tube at " + loc);
                }

                ps.setString(1, locations.worldName(loc));
                ps.setInt(2, locations.blockX(loc));
                ps.setInt(3, locations.blockY(loc));
                ps.setInt(4, locations.blockZ(loc));

                ps.addBatch();
            }

            ps.executeBatch();
        });

        debug.log("saveAll", "Finished saving tubes");
    }

    public void loadAll() {
        debug.log("loadAll", "Loading tubes from database");

        tubes.clear();
        tubesByChunk.clear();

        Database.run(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT * FROM tubes");

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                Object w = locations.worldByName(world);
                if (w == null) {
                    debug.log("loadAll", "World not found: " + world + " — skipping tube");
                    continue;
                }

                Object loc = locations.createLocation(w, x, y, z);

                if (DebugFlags.STARTUP_LOAD_LOGGING) {
                    debug.log("loadAll", "Loaded tube at " + loc);
                }

                addTube(loc);
            }
        });

        debug.log("loadAll", "Finished loading tubes");
    }

    private void indexAdd(Object loc) {
        ChunkKey key = locations.chunkKey(loc);
        tubesByChunk.computeIfAbsent(key, k -> new HashSet<>()).add(loc);
    }

    private void indexRemove(Object loc) {
        ChunkKey key = locations.chunkKey(loc);
        Set<Object> set = tubesByChunk.get(key);
        if (set == null) return;
        set.remove(loc);
        if (set.isEmpty()) tubesByChunk.remove(key);
    }
}
