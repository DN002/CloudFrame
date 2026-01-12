package dev.cloudframe.fabric.power;

import dev.cloudframe.common.power.probe.PowerProbeLegacy;

public final class PowerProbePackets {

    private PowerProbePackets() {
    }

    public static final int TYPE_NONE = PowerProbeLegacy.TYPE_NONE;
    public static final int TYPE_CABLE = PowerProbeLegacy.TYPE_CABLE;
    public static final int TYPE_STRATUS_PANEL = PowerProbeLegacy.TYPE_STRATUS_PANEL;
    public static final int TYPE_CLOUD_TURBINE = PowerProbeLegacy.TYPE_CLOUD_TURBINE;
    public static final int TYPE_QUARRY_CONTROLLER = PowerProbeLegacy.TYPE_QUARRY_CONTROLLER;
}
