package dev.cloudframe.fabric.platform;

import dev.cloudframe.common.platform.EventSystem;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Fabric implementation of EventSystem (SKELETON ONLY).
 * 
 * TODO: Implement using Fabric's event system:
 * - ServerTickEvents for tick scheduling
 * - Event registration via Fabric API
 */
public class FabricEventSystem implements EventSystem {

    @Override
    public void registerListener(Object listener) {
        // TODO: Register event listener using Fabric event API
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }

    @Override
    public Object scheduleRepeatingTask(Consumer<Long> task, long initialDelayTicks, long intervalTicks) {
        // TODO: Schedule repeating task using ServerTickEvents
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }

    @Override
    public Object scheduleTask(Runnable task, long delayTicks) {
        // TODO: Schedule one-time delayed task
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }

    @Override
    public void runOnMainThread(Runnable task) {
        // TODO: Check if on server thread, otherwise queue task
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }

    @Override
    public long getCurrentTick() {
        // TODO: Return current server tick count
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }

    @Override
    public Logger getLogger() {
        // TODO: Return SLF4J logger wrapped as java.util.logging.Logger
        throw new UnsupportedOperationException("Fabric EventSystem not yet implemented");
    }
}
