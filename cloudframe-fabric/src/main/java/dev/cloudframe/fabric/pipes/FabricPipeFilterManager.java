package dev.cloudframe.fabric.pipes;

import dev.cloudframe.common.pipes.filter.PipeFilterKey;
import dev.cloudframe.common.pipes.filter.PipeFilterRepository;
import dev.cloudframe.common.pipes.filter.PipeFilterState;
import dev.cloudframe.common.pipes.filter.PipeFilterService;
import dev.cloudframe.common.pipes.filter.InMemoryPipeFilterService;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/**
 * Stores per-pipe-side item filters.
 *
 * Data is persisted into the shared SQLite database table {@code pipe_filters}.
 */
public final class FabricPipeFilterManager {

    public static final int MODE_WHITELIST = PipeFilterState.MODE_WHITELIST;
    public static final int MODE_BLACKLIST = PipeFilterState.MODE_BLACKLIST;

    public static final int SLOT_COUNT = PipeFilterState.SLOT_COUNT;

    public static final class FilterState {
        private int mode;
        private final ItemStack[] items = new ItemStack[SLOT_COUNT];

        private FilterState(int mode, ItemStack[] initial) {
            this.mode = mode;
            Arrays.fill(this.items, ItemStack.EMPTY);
            if (initial != null) {
                for (int i = 0; i < Math.min(SLOT_COUNT, initial.length); i++) {
                    this.items[i] = (initial[i] == null) ? ItemStack.EMPTY : initial[i].copy();
                    if (!this.items[i].isEmpty()) {
                        this.items[i].setCount(1);
                    }
                }
            }
        }

        public int mode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = (mode == MODE_BLACKLIST) ? MODE_BLACKLIST : MODE_WHITELIST;
        }

        public ItemStack getStack(int slot) {
            if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
            ItemStack s = items[slot];
            return s == null ? ItemStack.EMPTY : s;
        }

        public void setStack(int slot, ItemStack stack) {
            if (slot < 0 || slot >= SLOT_COUNT) return;
            if (stack == null || stack.isEmpty()) {
                items[slot] = ItemStack.EMPTY;
            } else {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                items[slot] = copy;
            }
        }

        public ItemStack[] copyStacks() {
            ItemStack[] out = new ItemStack[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack s = items[i];
                out[i] = (s == null) ? ItemStack.EMPTY : s.copy();
                if (!out[i].isEmpty()) {
                    out[i].setCount(1);
                }
            }
            return out;
        }

        private boolean isEmptyFilterList() {
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack s = items[i];
                if (s != null && !s.isEmpty()) return false;
            }
            return true;
        }

        private boolean containsItem(Item item) {
            if (item == null) return false;
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack s = items[i];
                if (s != null && !s.isEmpty() && s.getItem() == item) {
                    return true;
                }
            }
            return false;
        }

        public boolean allows(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;

            boolean emptyList = isEmptyFilterList();
            boolean matched = containsItem(stack.getItem());

            if (mode == MODE_BLACKLIST) {
                // Empty blacklist -> allow everything.
                return emptyList || !matched;
            }

            // Whitelist:
            // Empty whitelist -> allow nothing.
            return !emptyList && matched;
        }
    }

    private final MinecraftServer server;
    private final PipeFilterService service;

    public FabricPipeFilterManager(MinecraftServer server) {
        this.server = server;
        this.service = new InMemoryPipeFilterService();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void loadAll() {
        service.loadAll();

        HashSet<GlobalPos> touchedPipes = new HashSet<>();
        for (PipeFilterKey k : service.keys()) {
            if (k == null) continue;

            RegistryKey<World> key = World.OVERWORLD;
            String worldId = k.worldId();
            if (worldId != null && !worldId.isBlank()) {
                try {
                    key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldId));
                } catch (Throwable ignored) {
                    key = World.OVERWORLD;
                }
            }

            BlockPos pos = new BlockPos(k.x(), k.y(), k.z());
            touchedPipes.add(GlobalPos.create(key, pos));
        }

        // Best-effort: update tube blockstates so filter attachments are visible.
        for (GlobalPos gp : touchedPipes) {
            if (gp == null) continue;
            var w = server.getWorld(gp.dimension());
            if (w == null) continue;
            if (!w.isChunkLoaded(gp.pos())) continue;
            dev.cloudframe.fabric.content.TubeBlock.refreshConnections(w, gp.pos());
        }
    }

    public boolean hasFilter(GlobalPos pipePos, int sideIndex) {
        return service.hasFilter(toPortableKey(pipePos, sideIndex));
    }

    public FilterState getOrCreate(GlobalPos pipePos, int sideIndex) {
        PipeFilterState st = service.getOrCreate(toPortableKey(pipePos, sideIndex));
        if (st == null) return null;
        return new FilterState(st.mode(), toItemStacks(st));
    }

    public FilterState get(GlobalPos pipePos, int sideIndex) {
        PipeFilterState st = service.get(toPortableKey(pipePos, sideIndex));
        if (st == null) return null;
        return new FilterState(st.mode(), toItemStacks(st));
    }

    public boolean allows(GlobalPos pipePos, int sideIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String itemId = id == null ? null : id.toString();
        return service.allows(toPortableKey(pipePos, sideIndex), itemId);
    }

    public void setMode(GlobalPos pipePos, int sideIndex, int mode) {
        service.setMode(toPortableKey(pipePos, sideIndex), mode);
    }

    public void setItems(GlobalPos pipePos, int sideIndex, ItemStack[] stacks) {
        if (stacks == null) stacks = new ItemStack[0];
        String[] itemIds = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack s = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
            if (s == null || s.isEmpty()) {
                itemIds[i] = null;
            } else {
                Identifier id = Registries.ITEM.getId(s.getItem());
                itemIds[i] = id == null ? null : id.toString();
            }
        }
        service.setItems(toPortableKey(pipePos, sideIndex), itemIds);
    }

    public void removeFilter(GlobalPos pipePos, int sideIndex) {
        service.remove(toPortableKey(pipePos, sideIndex));
    }

    public void removeAllAt(GlobalPos pipePos) {
        if (pipePos == null) return;

        BlockPos pos = pipePos.pos();
        String world = pipePos.dimension().getValue().toString();
        service.removeAllAt(world, pos.getX(), pos.getY(), pos.getZ());
    }

    private static PipeFilterKey toPortableKey(GlobalPos pipePos, int sideIndex) {
        if (pipePos == null) return null;
        BlockPos pos = pipePos.pos();
        String worldId = pipePos.dimension().getValue().toString();
        return new PipeFilterKey(worldId, pos.getX(), pos.getY(), pos.getZ(), sideIndex);
    }

    private static ItemStack[] toItemStacks(PipeFilterState st) {
        ItemStack[] out = new ItemStack[SLOT_COUNT];
        Arrays.fill(out, ItemStack.EMPTY);
        if (st == null) return out;

        String[] ids = st.copyItemIds();
        for (int i = 0; i < Math.min(SLOT_COUNT, ids.length); i++) {
            String id = ids[i];
            if (id == null || id.isBlank()) continue;
            try {
                Item item = Registries.ITEM.get(Identifier.of(id.trim()));
                if (item != null) {
                    out[i] = new ItemStack(item, 1);
                }
            } catch (Throwable ignored) {
                // ignore bad ids
            }
        }
        return out;
    }
}
