package dev.cloudframe.common.platform.items;

/**
 * Platform adapter for item stack operations.
 *
 * @param <STACK> platform stack type (e.g., Bukkit ItemStack, Fabric ItemStack)
 */
public interface ItemStackAdapter<STACK> {

    boolean isEmpty(STACK stack);

    int getCount(STACK stack);

    void setCount(STACK stack, int count);

    int getMaxCount(STACK stack);

    STACK copy(STACK stack);

    /** Returns true when two stacks can be merged (same item + components/metadata as needed). */
    boolean canMerge(STACK existing, STACK incoming);
}
