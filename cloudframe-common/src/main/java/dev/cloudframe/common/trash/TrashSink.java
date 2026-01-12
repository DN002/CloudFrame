package dev.cloudframe.common.trash;

/**
 * Platform-agnostic marker for inventories/blocks that act as a void.
 *
 * <p>Implementations should consume (delete) items and return how many were accepted.</p>
 */
public interface TrashSink {

    /**
     * Accepts (deletes) items from the given platform-specific stack object.
     *
     * @param itemStack platform stack instance (e.g., Fabric ItemStack, Bukkit ItemStack)
     * @return number of items accepted (0..stackCount)
     */
    int accept(Object itemStack);
}
