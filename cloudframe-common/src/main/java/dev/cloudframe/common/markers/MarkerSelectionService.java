package dev.cloudframe.common.markers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-agnostic marker selection cache + operations.
 */
public interface MarkerSelectionService {

    void loadAll();

    void saveAll();

    Set<UUID> players();

    MarkerSelectionState get(UUID playerId);

    Map<UUID, MarkerSelectionState> snapshot();

    int addCorner(UUID playerId, String worldId, int x, int y, int z);

    void clearCorners(UUID playerId);

    List<MarkerPos> getCorners(UUID playerId);

    boolean isComplete(UUID playerId);

    boolean isActivated(UUID playerId);

    void activateFrame(UUID playerId);

    void setFrameFromCorners(UUID playerId, String worldId, List<MarkerPos> corners, boolean activated, boolean persist);
}
