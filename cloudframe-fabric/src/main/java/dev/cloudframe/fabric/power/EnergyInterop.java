package dev.cloudframe.fabric.power;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dev.cloudframe.fabric.content.CloudCellBlockEntity;
import dev.cloudframe.common.util.Debug;
import dev.cloudframe.common.util.DebugManager;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Optional interoperability with external Fabric energy APIs.
 *
 * Currently supports TeamReborn Energy (used by Tech Reborn / Modern Industrialization)
 * via reflection so CloudFrame does not require a hard dependency.
 */
public final class EnergyInterop {

    private static final Debug debug = DebugManager.get(EnergyInterop.class);

    /** 1 external energy unit == 1 CFE. */
    public static final long EXTERNAL_ENERGY_TO_CFE = 1L;

    private static final String TR_ENERGY_STORAGE_CLASS = "team.reborn.energy.api.EnergyStorage";

    private static final boolean TR_PRESENT = isClassPresent(TR_ENERGY_STORAGE_CLASS);

    private EnergyInterop() {
    }

    public static boolean isAvailable() {
        return TR_PRESENT;
    }

    public static void tryRegisterCloudCell(BlockEntityType<CloudCellBlockEntity> cloudCellType) {
        if (!TR_PRESENT) return;

        try {
            Class<?> energyStorageClass = Class.forName(TR_ENERGY_STORAGE_CLASS);
            Object sidedLookup = getStaticField(energyStorageClass, "SIDED");
            if (sidedLookup == null) return;

            Method registerMethod = findRegisterMethod(sidedLookup.getClass());
            if (registerMethod == null) {
                debug.log("interop", "TeamReborn Energy present, but no registerForBlockEntity method found");
                return;
            }

            Class<?> providerInterface = registerMethod.getParameterTypes()[0];
            Object provider = Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[] { providerInterface },
                (proxy, method, args) -> {
                    if (args == null) return null;

                    BlockEntity be = null;
                    Direction side = null;

                    for (Object a : args) {
                        if (a instanceof BlockEntity) be = (BlockEntity) a;
                        if (a instanceof Direction) side = (Direction) a;
                    }

                    if (!(be instanceof CloudCellBlockEntity cell)) return null;

                    // Some APIs allow null side contexts; that's fine for us.
                    return createEnergyStorageProxy(energyStorageClass, cell, side);
                }
            );

            // Invoke register method with whatever signature the lookup exposes.
            if (registerMethod.getParameterCount() == 2) {
                Class<?> t1 = registerMethod.getParameterTypes()[1];
                if (t1.isArray() && t1.getComponentType().isAssignableFrom(BlockEntityType.class)) {
                    Object arr = java.lang.reflect.Array.newInstance(t1.getComponentType(), 1);
                    java.lang.reflect.Array.set(arr, 0, cloudCellType);
                    registerMethod.invoke(sidedLookup, provider, arr);
                } else {
                    registerMethod.invoke(sidedLookup, provider, cloudCellType);
                }
            } else {
                // Fallback: try to pass the type as the last argument.
                Object[] callArgs = new Object[registerMethod.getParameterCount()];
                callArgs[0] = provider;
                for (int i = 1; i < callArgs.length; i++) {
                    callArgs[i] = cloudCellType;
                }
                registerMethod.invoke(sidedLookup, callArgs);
            }

            debug.log("interop", "Registered Cloud Cell as TeamReborn Energy storage (soft-dep)");
        } catch (Throwable t) {
            debug.log("interop", "Failed registering TeamReborn energy storage: " + t.getMessage());
        }
    }

    public static long tryExtractExternalCfe(ServerWorld world, BlockPos externalPos, Direction side, long maxCfe) {
        if (!TR_PRESENT) return 0L;
        if (maxCfe <= 0L) return 0L;
        if (world == null) return 0L;

        long requestExternal = maxCfe / EXTERNAL_ENERGY_TO_CFE;
        if (requestExternal <= 0L) return 0L;

        try {
            Object storage = findTrEnergyStorage(world, externalPos, side);
            if (storage == null) return 0L;

            try (Transaction tx = Transaction.openOuter()) {
                long extractedExternal = invokeExtract(storage, requestExternal, tx);
                if (extractedExternal > 0L) {
                    tx.commit();
                    return extractedExternal * EXTERNAL_ENERGY_TO_CFE;
                }
                return 0L;
            }
        } catch (Throwable t) {
            return 0L;
        }
    }

    public record ExternalEnergyInfo(long storedCfe, long capacityCfe) {
    }

    /**
     * Best-effort read-only measurement of external energy storage adjacent to a cable.
     * This should NOT consume energy.
     */
    public static ExternalEnergyInfo tryMeasureExternalCfe(ServerWorld world, BlockPos externalPos, Direction side) {
        if (!TR_PRESENT) return null;
        if (world == null || externalPos == null) return null;

        try {
            Object storage = findTrEnergyStorage(world, externalPos, side);
            if (storage == null) return null;

            long storedExternal = invokeLongGetter(storage, "getAmount", "getStored", "getEnergy");
            long capacityExternal = invokeLongGetter(storage, "getCapacity", "getMaxAmount", "getMaxEnergy");

            long stored = Math.max(0L, storedExternal) * EXTERNAL_ENERGY_TO_CFE;
            long capacity = Math.max(0L, capacityExternal) * EXTERNAL_ENERGY_TO_CFE;

            return new ExternalEnergyInfo(stored, capacity);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object findTrEnergyStorage(ServerWorld world, BlockPos pos, Direction side) throws Exception {
        Class<?> energyStorageClass = Class.forName(TR_ENERGY_STORAGE_CLASS);
        Object sidedLookup = getStaticField(energyStorageClass, "SIDED");
        if (sidedLookup == null) return null;

        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);

        for (Method m : sidedLookup.getClass().getMethods()) {
            if (!m.getName().equals("find")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length < 2) continue;

            Object[] args = new Object[p.length];
            boolean ok = true;

            for (int i = 0; i < p.length; i++) {
                Class<?> pt = p[i];
                if (pt.isAssignableFrom(world.getClass()) || pt.getName().endsWith("World")) {
                    args[i] = world;
                } else if (pt.isAssignableFrom(BlockPos.class)) {
                    args[i] = pos;
                } else if (pt.isAssignableFrom(BlockState.class)) {
                    args[i] = state;
                } else if (pt.isAssignableFrom(BlockEntity.class)) {
                    args[i] = be;
                } else if (pt.isAssignableFrom(Direction.class)) {
                    args[i] = side;
                } else {
                    // Unknown param type; best-effort null.
                    args[i] = null;
                }
            }

            try {
                Object out = m.invoke(sidedLookup, args);
                if (out != null) return out;
            } catch (IllegalArgumentException ex) {
                ok = false;
            }

            if (!ok) continue;
        }

        return null;
    }

    private static long invokeExtract(Object storage, long amount, Object tx) throws Exception {
        for (Method m : storage.getClass().getMethods()) {
            if (!m.getName().equals("extract")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == long.class) {
                return (long) m.invoke(storage, amount, tx);
            }
            if (p.length == 1 && p[0] == long.class) {
                return (long) m.invoke(storage, amount);
            }
        }
        return 0L;
    }

    private static long invokeLongGetter(Object storage, String... methodNames) {
        if (storage == null || methodNames == null) return 0L;
        for (String name : methodNames) {
            if (name == null) continue;
            for (Method m : storage.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 0) continue;
                if (m.getReturnType() != long.class) continue;
                try {
                    return (long) m.invoke(storage);
                } catch (Throwable t) {
                    // try next
                }
            }
        }
        return 0L;
    }

    private static Object createEnergyStorageProxy(Class<?> energyStorageClass, CloudCellBlockEntity cell, Direction side) {
        return Proxy.newProxyInstance(
            energyStorageClass.getClassLoader(),
            new Class<?>[] { energyStorageClass },
            (proxy, method, args) -> {
                String name = method.getName();

                if (name.equals("insert")) {
                    long max = args != null && args.length >= 1 && args[0] instanceof Long l ? l : 0L;
                    return cell.insertCfe(max);
                }

                if (name.equals("extract")) {
                    long max = args != null && args.length >= 1 && args[0] instanceof Long l ? l : 0L;
                    return cell.extractCfe(max);
                }

                if (name.equals("getAmount") || name.equals("getStored") || name.equals("getEnergy")) {
                    return cell.getStoredCfe();
                }

                if (name.equals("getCapacity") || name.equals("getMaxAmount") || name.equals("getMaxEnergy")) {
                    return (long) CloudCellBlockEntity.CAPACITY_CFE;
                }

                if (name.equals("supportsInsertion") || name.equals("canInsert")) {
                    return true;
                }

                if (name.equals("supportsExtraction") || name.equals("canExtract")) {
                    return true;
                }

                if (method.getReturnType() == long.class) return 0L;
                if (method.getReturnType() == boolean.class) return false;
                return null;
            }
        );
    }

    private static Method findRegisterMethod(Class<?> sidedLookupClass) {
        for (Method m : sidedLookupClass.getMethods()) {
            String n = m.getName();
            if (!(n.equals("registerForBlockEntity") || n.equals("registerForBlockEntities"))) continue;
            if (m.getParameterCount() < 2) continue;
            return m;
        }
        return null;
    }

    private static Object getStaticField(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getField(fieldName);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isClassPresent(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
