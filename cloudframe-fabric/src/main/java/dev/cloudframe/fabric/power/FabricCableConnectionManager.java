package dev.cloudframe.fabric.power;

import dev.cloudframe.common.power.cables.CableConnectionService;
import dev.cloudframe.common.power.cables.CableKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Stores per-cable disabled external connection sides.
 *
 * This is used for wrench toggling of Cloud Cable connection arms (to panels/turbines/cells/controllers/external).
 * Disabled sides are persisted into the shared SQLite database.
 */
public final class FabricCableConnectionManager {

    private final MinecraftServer server;
    private final CableConnectionService service;

    public FabricCableConnectionManager(MinecraftServer server, CableConnectionService service) {
        this.server = server;
        this.service = service;
    }

    public void loadAll() {
        if (service != null) {
            service.loadAll();
        }
    }

    public boolean isSideDisabled(GlobalPos cablePos, int dirIndex) {
        CableKey key = toKey(cablePos);
        return service != null && service.isSideDisabled(key, dirIndex);
    }

    public int getDisabledSides(GlobalPos cablePos) {
        CableKey key = toKey(cablePos);
        return service == null ? 0 : service.getDisabledSidesMask(key);
    }

    public void toggleSide(GlobalPos cablePos, int dirIndex) {
        CableKey key = toKey(cablePos);
        if (service == null) return;
        service.toggleSide(key, dirIndex);
    }

    public void setSideDisabled(GlobalPos cablePos, int dirIndex, boolean disabled) {
        CableKey key = toKey(cablePos);
        if (service == null) return;
        service.setSideDisabled(key, dirIndex, disabled);
    }

    private static CableKey toKey(GlobalPos cablePos) {
        if (cablePos == null) return null;
        String worldId = cablePos.dimension().getValue().toString();
        BlockPos pos = cablePos.pos();
        return new CableKey(worldId, pos.getX(), pos.getY(), pos.getZ());
    }
}
