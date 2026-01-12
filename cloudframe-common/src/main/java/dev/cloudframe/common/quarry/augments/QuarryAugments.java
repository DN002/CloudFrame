package dev.cloudframe.common.quarry.augments;

/**
 * Platform-agnostic representation of quarry augments.
 *
 * <p>This is intentionally decoupled from any item stack type so platforms can
 * map their own items/UI onto this shared model.</p>
 */
public record QuarryAugments(
    boolean silkTouch,
    int speedTier,
    int fortuneTier
) {
    public QuarryAugments {
        speedTier = QuarryAugmentRules.clampTier(speedTier);
        fortuneTier = QuarryAugmentRules.clampTier(fortuneTier);
        if (silkTouch && fortuneTier > 0) {
            // Silk and Fortune are mutually exclusive; keep Silk, clear Fortune.
            fortuneTier = 0;
        }
    }
}
