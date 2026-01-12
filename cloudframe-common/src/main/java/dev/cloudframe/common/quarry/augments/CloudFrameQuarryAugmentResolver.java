package dev.cloudframe.common.quarry.augments;

import dev.cloudframe.common.ids.CloudFrameIds;
import dev.cloudframe.common.platform.items.ItemFromStackAdapter;
import dev.cloudframe.common.platform.items.ItemIdRegistry;
import dev.cloudframe.common.platform.items.ItemStackAdapter;

/**
 * Shared resolver for CloudFrame's standard augment items.
 *
 * <p>This is intentionally based on stable item ids so it can be reused by both Fabric
 * and Bukkit implementations (with different underlying item/stack types).</p>
 */
public final class CloudFrameQuarryAugmentResolver<STACK, ITEM> implements QuarryAugmentResolver<STACK> {

    private final ItemStackAdapter<STACK> stacks;
    private final ItemFromStackAdapter<STACK, ITEM> itemFromStack;
    private final ItemIdRegistry<ITEM> itemIds;

    public CloudFrameQuarryAugmentResolver(
        ItemStackAdapter<STACK> stacks,
        ItemFromStackAdapter<STACK, ITEM> itemFromStack,
        ItemIdRegistry<ITEM> itemIds
    ) {
        this.stacks = stacks;
        this.itemFromStack = itemFromStack;
        this.itemIds = itemIds;
    }

    @Override
    public QuarryAugments resolve(STACK stack) {
        if (stack == null) return null;
        if (stacks != null && stacks.isEmpty(stack)) return null;

        ITEM item = itemFromStack == null ? null : itemFromStack.itemOf(stack);
        if (item == null) return null;

        String id = itemIds == null ? null : itemIds.idOf(item);
        if (id == null || id.isBlank()) return null;

        // Accept either fully-qualified ids (cloudframe:speed_augment_1) or bare paths.
        if (matches(id, CloudFrameIds.SILK_TOUCH_AUGMENT)) {
            return new QuarryAugments(true, 0, 0);
        }

        int speed = tierFromId(id, CloudFrameIds.SPEED_AUGMENT_1, CloudFrameIds.SPEED_AUGMENT_2, CloudFrameIds.SPEED_AUGMENT_3);
        if (speed > 0) {
            return new QuarryAugments(false, speed, 0);
        }

        int fortune = tierFromId(id, CloudFrameIds.FORTUNE_AUGMENT_1, CloudFrameIds.FORTUNE_AUGMENT_2, CloudFrameIds.FORTUNE_AUGMENT_3);
        if (fortune > 0) {
            return new QuarryAugments(false, 0, fortune);
        }

        return null;
    }

    private static int tierFromId(String id, String t1, String t2, String t3) {
        if (matches(id, t1)) return 1;
        if (matches(id, t2)) return 2;
        if (matches(id, t3)) return 3;
        return 0;
    }

    private static boolean matches(String id, String path) {
        if (id == null || path == null) return false;
        if (id.equals(path)) return true;
        String fq = CloudFrameIds.MOD_ID + ":" + path;
        return fq.equals(id);
    }
}
