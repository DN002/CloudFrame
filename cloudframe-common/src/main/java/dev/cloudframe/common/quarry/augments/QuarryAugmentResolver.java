package dev.cloudframe.common.quarry.augments;

/**
 * Platform-agnostic resolver that interprets an item stack as a quarry augment.
 *
 * @param <STACK> platform stack type
 */
public interface QuarryAugmentResolver<STACK> {

    /**
     * @return augment info for the given stack, or null if the stack is not an augment.
     */
    QuarryAugments resolve(STACK stack);
}
