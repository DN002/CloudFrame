package dev.cloudframe.common.power.probe;

/**
 * Platform-agnostic representation of what the player is probing.
 *
 * <p>This mirrors the fields of the Fabric {@code PowerProbeResponsePayload} so platforms
 * can share the semantics and formatting while keeping their own networking implementations.</p>
 */
public record PowerProbeSnapshot(
    long posLong,
    PowerProbeType type,
    long producedCfePerTick,
    long storedCfe,
    boolean externalApiPresent,
    int externalEndpointCount,
    long externalStoredCfe,
    long externalCapacityCfe
) {
    public PowerProbeSnapshot {
        if (type == null) type = PowerProbeType.NONE;
        producedCfePerTick = Math.max(0L, producedCfePerTick);
        storedCfe = Math.max(0L, storedCfe);
        externalEndpointCount = Math.max(0, externalEndpointCount);
        externalStoredCfe = Math.max(0L, externalStoredCfe);
        externalCapacityCfe = Math.max(0L, externalCapacityCfe);
    }
}
