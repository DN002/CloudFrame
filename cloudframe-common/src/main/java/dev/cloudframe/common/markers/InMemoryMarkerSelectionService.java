package dev.cloudframe.common.markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default portable implementation: in-memory cache backed by SQLite persistence.
 */
public final class InMemoryMarkerSelectionService implements MarkerSelectionService {

    private final Map<UUID, MarkerSelectionState> selections = new HashMap<>();

    @Override
    public void loadAll() {
        selections.clear();
        selections.putAll(MarkerSelectionRepository.loadAll());
    }

    @Override
    public void saveAll() {
        MarkerSelectionRepository.saveAll(selections);
    }

    @Override
    public Set<UUID> players() {
        return Collections.unmodifiableSet(new HashSet<>(selections.keySet()));
    }

    @Override
    public MarkerSelectionState get(UUID playerId) {
        return playerId == null ? null : selections.get(playerId);
    }

    @Override
    public Map<UUID, MarkerSelectionState> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(selections));
    }

    @Override
    public int addCorner(UUID playerId, String worldId, int x, int y, int z) {
        if (playerId == null) return -1;

        MarkerSelectionState cur = selections.get(playerId);
        List<MarkerPos> corners = cur != null ? new ArrayList<>(cur.corners()) : new ArrayList<>();

        // Enforce same world for all corners.
        if (cur != null && cur.worldId() != null && worldId != null && !cur.worldId().equals(worldId)) {
            selections.remove(playerId);
            return -1;
        }

        int expectedY = cur != null ? cur.yLevel() : y;

        // Check Y level matches
        if (!corners.isEmpty() && y != expectedY) {
            selections.remove(playerId);
            return -1;
        }

        MarkerPos pos = new MarkerPos(x, y, z);

        // Prevent duplicates at same location
        for (MarkerPos c : corners) {
            if (pos.equals(c)) {
                return corners.size();
            }
        }

        corners.add(pos);
        int cornerNum = corners.size();

        selections.put(playerId, new MarkerSelectionState(corners, expectedY, false, worldId));

        if (corners.size() == 4) {
            MarkerSelectionRepository.upsert(playerId, selections.get(playerId));
        }

        return cornerNum;
    }

    @Override
    public void clearCorners(UUID playerId) {
        if (playerId == null) return;
        selections.remove(playerId);
    }

    @Override
    public List<MarkerPos> getCorners(UUID playerId) {
        MarkerSelectionState sel = get(playerId);
        return sel != null ? new ArrayList<>(sel.corners()) : new ArrayList<>();
    }

    @Override
    public boolean isComplete(UUID playerId) {
        MarkerSelectionState sel = get(playerId);
        return sel != null && sel.isComplete();
    }

    @Override
    public boolean isActivated(UUID playerId) {
        MarkerSelectionState sel = get(playerId);
        return sel != null && sel.activated();
    }

    @Override
    public void activateFrame(UUID playerId) {
        MarkerSelectionState cur = get(playerId);
        if (cur == null || !cur.isComplete()) return;
        cur.setActivated(true);
    }

    @Override
    public void setFrameFromCorners(UUID playerId, String worldId, List<MarkerPos> corners, boolean activated, boolean persist) {
        if (playerId == null) return;
        if (corners == null || corners.size() != 4) {
            selections.remove(playerId);
            return;
        }

        int y = corners.get(0).y();
        List<MarkerPos> frozen = new ArrayList<>(4);
        for (MarkerPos c : corners) {
            if (c == null) continue;
            frozen.add(new MarkerPos(c.x(), y, c.z()));
        }

        MarkerSelectionState st = new MarkerSelectionState(frozen, y, activated, worldId);
        selections.put(playerId, st);

        if (persist) {
            MarkerSelectionRepository.upsert(playerId, st);
        }
    }
}
