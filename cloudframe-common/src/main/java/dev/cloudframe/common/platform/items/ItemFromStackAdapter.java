package dev.cloudframe.common.platform.items;

/**
 * Platform adapter for getting the underlying item type from a platform stack type.
 *
 * <p>This exists so shared logic can reason about items (via {@link ItemIdRegistry})
 * without depending on a specific stack implementation.</p>
 */
public interface ItemFromStackAdapter<STACK, ITEM> {
    ITEM itemOf(STACK stack);
}
