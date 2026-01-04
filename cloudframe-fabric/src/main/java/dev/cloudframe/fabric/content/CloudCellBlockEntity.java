package dev.cloudframe.fabric.content;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

/**
 * Simple battery storage for the Cloud Cable power network.
 */
public class CloudCellBlockEntity extends BlockEntity {

    public static final int CAPACITY_CFE = 1_000_000;

    private int storedCfe = 0;

    public CloudCellBlockEntity(BlockPos pos, BlockState state) {
        super(CloudFrameContent.getCloudCellBlockEntity(), pos, state);
    }

    public long getStoredCfe() {
        return storedCfe;
    }

    public long insertCfe(long amount) {
        if (amount <= 0L) return 0L;
        int space = CAPACITY_CFE - storedCfe;
        if (space <= 0) return 0L;
        long insertedLong = Math.min((long) space, amount);
        int inserted = (int) insertedLong;
        storedCfe += inserted;
        markDirty();
        return insertedLong;
    }

    public long extractCfe(long amount) {
        if (amount <= 0L) return 0L;
        if (storedCfe <= 0) return 0L;
        long extractedLong = Math.min((long) storedCfe, amount);
        int extracted = (int) extractedLong;
        storedCfe -= extracted;
        markDirty();
        return extractedLong;
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putInt("StoredCfe", storedCfe);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        storedCfe = Math.max(0, Math.min(CAPACITY_CFE, view.getInt("StoredCfe", 0)));
    }
}
