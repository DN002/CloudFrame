package dev.cloudframe.common.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small helper for one-time warnings.
 *
 * <p>This is useful for portability layers where we want to surface a potentially
 * dangerous fallback (like assuming a default world) without spamming logs every tick.</p>
 */
public final class WarnOnce {

    private WarnOnce() {
    }

    public static void warn(AtomicBoolean gate, Debug debug, String context, String message) {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(debug, "debug");
        if (gate.compareAndSet(false, true)) {
            debug.log(context, message);
        }
    }
}
