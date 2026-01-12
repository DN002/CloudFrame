package dev.cloudframe.common.power.probe;

/**
 * Legacy wire-format ids for the Fabric Power Probe protocol.
 *
 * <p>These are compile-time constants so they can be used in switch case labels.</p>
 */
public final class PowerProbeLegacy {

    private PowerProbeLegacy() {
    }

    public static final int TYPE_NONE = 0;
    public static final int TYPE_CABLE = 1;
    public static final int TYPE_STRATUS_PANEL = 2;
    public static final int TYPE_CLOUD_TURBINE = 3;
    public static final int TYPE_QUARRY_CONTROLLER = 4;
}
