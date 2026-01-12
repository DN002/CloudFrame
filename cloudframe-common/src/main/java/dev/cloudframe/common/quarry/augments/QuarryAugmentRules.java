package dev.cloudframe.common.quarry.augments;

public final class QuarryAugmentRules {

    private QuarryAugmentRules() {
    }

    public static int clampTier(int tier) {
        return Math.max(0, Math.min(3, tier));
    }

    public static boolean isSilkTouchAllowedWithFortune(boolean silkTouch, int fortuneTier) {
        return !silkTouch || clampTier(fortuneTier) <= 0;
    }

    public static QuarryAugments normalize(boolean silkTouch, int speedTier, int fortuneTier) {
        return new QuarryAugments(silkTouch, speedTier, fortuneTier);
    }
}
