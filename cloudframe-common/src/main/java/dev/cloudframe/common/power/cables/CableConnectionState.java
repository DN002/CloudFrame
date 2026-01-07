package dev.cloudframe.common.power.cables;

/**
 * Platform-agnostic disabled-sides state for a cable.
 *
 * The mask uses the same 0..5 direction indexing as the rest of CloudFrame.
 */
public final class CableConnectionState {

    private int disabledSidesMask;

    public CableConnectionState(int disabledSidesMask) {
        this.disabledSidesMask = normalize(disabledSidesMask);
    }

    public int disabledSidesMask() {
        return disabledSidesMask;
    }

    public boolean isSideDisabled(int dirIndex) {
        if (dirIndex < 0 || dirIndex > 5) return false;
        return (disabledSidesMask & (1 << dirIndex)) != 0;
    }

    public void setDisabledSidesMask(int disabledSidesMask) {
        this.disabledSidesMask = normalize(disabledSidesMask);
    }

    public void toggleSide(int dirIndex) {
        if (dirIndex < 0 || dirIndex > 5) return;
        disabledSidesMask = normalize(disabledSidesMask ^ (1 << dirIndex));
    }

    public void setSideDisabled(int dirIndex, boolean disabled) {
        if (dirIndex < 0 || dirIndex > 5) return;
        if (disabled) {
            disabledSidesMask = normalize(disabledSidesMask | (1 << dirIndex));
        } else {
            disabledSidesMask = normalize(disabledSidesMask & ~(1 << dirIndex));
        }
    }

    private static int normalize(int mask) {
        return mask & 0x3F;
    }
}
