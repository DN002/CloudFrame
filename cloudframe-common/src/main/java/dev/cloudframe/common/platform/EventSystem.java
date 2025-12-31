package dev.cloudframe.common.platform;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Platform-agnostic interface for event registration and lifecycle management.
 */
public interface EventSystem {
    
    /**
     * Register an event listener (varies by platform).
     * listener should be a platform-specific listener class annotated appropriately.
     */
    void registerListener(Object listener);
    
    /**
     * Schedule a repeating task that receives the current tick number.
     * @return a task handle for cancellation (platform-specific)
     */
    Object scheduleRepeatingTask(Consumer<Long> task, long initialDelayTicks, long intervalTicks);
    
    /**
     * Schedule a one-time task.
     * @return a task handle for cancellation (platform-specific)
     */
    Object scheduleTask(Runnable task, long delayTicks);
    
    /**
     * Run a task on the main server thread.
     */
    void runOnMainThread(Runnable task);
    
    /**
     * Get the current server tick number.
     */
    long getCurrentTick();
    
    /**
     * Get the main logger.
     */
    Logger getLogger();
}
