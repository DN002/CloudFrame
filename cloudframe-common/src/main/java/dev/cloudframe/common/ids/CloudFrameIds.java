package dev.cloudframe.common.ids;

/**
 * Shared identifiers/constants that should remain consistent across platforms.
 *
 * <p>These are plain strings so both Fabric and Bukkit can consume them without
 * depending on each other's identifier types.</p>
 */
public final class CloudFrameIds {

    private CloudFrameIds() {
    }

    public static final String MOD_ID = "cloudframe";

    // Content ids (paths)
    public static final String CLOUD_PIPE = "cloud_pipe";
    public static final String CLOUD_CABLE = "cloud_cable";
    public static final String STRATUS_PANEL = "stratus_panel";
    public static final String CLOUD_TURBINE = "cloud_turbine";
    public static final String CLOUD_CELL = "cloud_cell";
    public static final String QUARRY_CONTROLLER = "quarry_controller";
    public static final String MARKER = "marker";
    public static final String WRENCH = "wrench";
    public static final String PIPE_FILTER = "pipe_filter";
    public static final String TRASH_CAN = "trash_can";

    public static final String SILK_TOUCH_AUGMENT = "silk_touch_augment";
    public static final String SPEED_AUGMENT_1 = "speed_augment_1";
    public static final String SPEED_AUGMENT_2 = "speed_augment_2";
    public static final String SPEED_AUGMENT_3 = "speed_augment_3";
    public static final String FORTUNE_AUGMENT_1 = "fortune_augment_1";
    public static final String FORTUNE_AUGMENT_2 = "fortune_augment_2";
    public static final String FORTUNE_AUGMENT_3 = "fortune_augment_3";

    // Networking (Fabric custom payload ids use these paths)
    public static final String POWER_PROBE_REQUEST = "power_probe_request";
    public static final String POWER_PROBE_RESPONSE = "power_probe_response";
}
