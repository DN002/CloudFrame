package dev.cloudframe.fabric.content;

import net.minecraft.block.Block;

/**
 * Placeholder power source block.
 *
 * For now this is just a normal block (no GUI) that Cloud Cables can connect to.
 * Future work will hook this into a real power network / consumers.
 */
public class StratusPanelBlock extends Block {

    public StratusPanelBlock(Settings settings) {
        super(settings);
    }
}
