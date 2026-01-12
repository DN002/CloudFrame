package dev.cloudframe.common.power.probe;

/**
 * Shared probe type identifiers.
 *
 * <p>The {@link #legacyId()} values intentionally match the Fabric wire protocol ints
 * used by the existing Power Probe payload, so multiple platforms can share a single
 * semantic model without changing the packet format.</p>
 */
public enum PowerProbeType {
    NONE(PowerProbeLegacy.TYPE_NONE),
    CABLE(PowerProbeLegacy.TYPE_CABLE),
    STRATUS_PANEL(PowerProbeLegacy.TYPE_STRATUS_PANEL),
    CLOUD_TURBINE(PowerProbeLegacy.TYPE_CLOUD_TURBINE),
    QUARRY_CONTROLLER(PowerProbeLegacy.TYPE_QUARRY_CONTROLLER);

    private final int legacyId;

    PowerProbeType(int legacyId) {
        this.legacyId = legacyId;
    }

    public int legacyId() {
        return legacyId;
    }

    public static PowerProbeType fromLegacyId(int id) {
        for (PowerProbeType t : values()) {
            if (t.legacyId == id) return t;
        }
        return NONE;
    }
}
