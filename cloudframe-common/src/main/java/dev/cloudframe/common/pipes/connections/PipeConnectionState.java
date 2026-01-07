package dev.cloudframe.common.pipes.connections;

/**
 * Per-pipe disabled side bitmask wrapper.
 *
 * Bit positions follow the shared direction index convention:
 * 0=EAST, 1=WEST, 2=UP, 3=DOWN, 4=SOUTH, 5=NORTH.
 */
public final class PipeConnectionState {

    private int disabledSidesMask;

    public PipeConnectionState(int disabledSidesMask) {
        this.disabledSidesMask = disabledSidesMask;
    }

    public int disabledSidesMask() {
        return disabledSidesMask;
    }

    public boolean isSideDisabled(int dirIndex) {
        if (dirIndex < 0 || dirIndex > 5) return false;
        int bit = 1 << dirIndex;
        return (disabledSidesMask & bit) != 0;
    }

    public void toggleSide(int dirIndex) {
        if (dirIndex < 0 || dirIndex > 5) return;
        disabledSidesMask ^= (1 << dirIndex);
    }

    public void setSideDisabled(int dirIndex, boolean disabled) {
        if (dirIndex < 0 || dirIndex > 5) return;
        int bit = 1 << dirIndex;
        if (disabled) disabledSidesMask |= bit;
        else disabledSidesMask &= ~bit;
    }
}
