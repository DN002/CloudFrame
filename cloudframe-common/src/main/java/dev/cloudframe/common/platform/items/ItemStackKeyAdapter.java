package dev.cloudframe.common.platform.items;

/**
 * Platform adapter for computing a stable key/signature for an item stack.
 *
 * <p>The key should be independent of stack count, but include anything that affects
 * merge behavior or routing decisions (e.g. item id + components/metadata).</p>
 */
public interface ItemStackKeyAdapter<STACK> {
    String key(STACK stack);
}
