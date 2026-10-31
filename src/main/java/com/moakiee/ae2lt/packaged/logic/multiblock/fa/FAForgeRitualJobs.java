package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persistent reservations bridge material insertion and native ritual start across server ticks. */
@EventBusSubscriber(modid = "ae2ltpp")
public final class FAForgeRitualJobs {
    private static final Set<BlockEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ConcurrentLinkedQueue<WeakReference<LevelChunk>> LOADED = new ConcurrentLinkedQueue<>();

    private FAForgeRitualJobs() {}

    static boolean pending(CompoundTag state) {
        return state.getString("mode").equals("waiting") || state.getString("mode").equals("refunding");
    }

    static void prepare(ServerLevel level, BlockEntity forge, CompoundTag state, FARituals.Entry ritual,
                        int[] cost, int[] need, int[] before, int[] supplied, int[] taken,
                        List<AEItemKey> keys, List<BlockEntity> pedestals) {
        state.putString("mode", "waiting");
        state.putString("recipe", ritual.id().toString());
        state.putLong("prepared_at", level.getGameTime());
        state.putIntArray("cost", cost);
        state.putIntArray("need", need);
        state.putIntArray("before", before);
        var reserved = new int[4];
        for (int i = 0; i < 4; i++) reserved[i] = Math.addExact(supplied[i], taken[i]);
        state.putIntArray("reserved", reserved);
        var targets = new ListTag();
        for (int i = 0; i < keys.size(); i++) {
            var target = new CompoundTag();
            target.putLong("pos", (i == 0 ? forge : pedestals.get(i - 1)).getBlockPos().asLong());
            target.put("item", keys.get(i).toStack().save(level.registryAccess()));
            targets.add(target);
        }
        state.put("targets", targets);
        forge.setChanged();
        track(forge);
    }

    static void track(BlockEntity forge) { TRACKED.add(forge); }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        // Chunk load can precede FULL status; do not resolve block entities inside this event.
        if (event.getChunk() instanceof LevelChunk chunk && event.getLevel() instanceof ServerLevel)
            LOADED.add(new WeakReference<>(chunk));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (WeakReference<LevelChunk> ref; (ref = LOADED.poll()) != null;) {
            var chunk = ref.get();
            if (chunk == null || !(chunk.getLevel() instanceof ServerLevel level)
                    || level.getServer() != event.getServer() || !level.hasChunk(chunk.getPos().x, chunk.getPos().z)) continue;
            for (var pos : chunk.getBlockEntitiesPos()) {
                var forge = chunk.getBlockEntity(pos);
                if (forge != null && pending(forge.getPersistentData().getCompound(FAForgeAdapter.JOB))) {
                    // Loading pedestals can queue another material update. Let that inactive state flush too.
                    forge.getPersistentData().getCompound(FAForgeAdapter.JOB).putLong("prepared_at", level.getGameTime());
                    track(forge);
                }
            }
        }
        for (var forge : List.copyOf(TRACKED)) {
            if (!(forge.getLevel() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            if (!FAAccess.live(forge)) {
                TRACKED.remove(forge);
                if (level.isLoaded(forge.getBlockPos())) {
                    var replacement = level.getBlockEntity(forge.getBlockPos());
                    if (replacement != null && pending(replacement.getPersistentData().getCompound(FAForgeAdapter.JOB))) {
                        replacement.getPersistentData().getCompound(FAForgeAdapter.JOB).putLong("prepared_at", level.getGameTime());
                        track(replacement);
                    }
                }
                continue;
            }
            var state = forge.getPersistentData().getCompound(FAForgeAdapter.JOB);
            if (!pending(state)) { TRACKED.remove(forge); continue; }
            // Flush insertion's inactive update before native start can put an active ritual in it.
            if (level.getGameTime() <= state.getLong("prepared_at")) continue;
            try { advance(level, forge, state); }
            catch (RuntimeException | LinkageError failure) {
                CompatibilityDiagnostics.failure(FAForgeRitualJobs.class, "advance", failure);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { TRACKED.clear(); LOADED.clear(); }

    private static void advance(ServerLevel level, BlockEntity forge, CompoundTag state) {
        if (state.getString("mode").equals("refunding")) { refund(level, forge, state); return; }
        var cost = state.getIntArray("cost");
        var reserved = state.getIntArray("reserved");
        var need = state.getIntArray("need");
        if (cost.length != 4 || reserved.length != 4 || need.length != 4)
            throw new IllegalStateException("Malformed pending FA ritual reservation");
        if (!FAAccess.active(forge)) {
            var recipe = FARituals.all(level).stream().filter(r -> r.id().toString().equals(state.getString("recipe")))
                    .findFirst().orElse(null);
            if (recipe == null || !FAAccess.structure(level, forge.getBlockPos())
                    || !Arrays.equals(cost, FAForgeAdapter.cost(recipe.recipe())) || !targetsMatch(level, state)) {
                cancel(level, forge, state); return;
            }
            var before = new int[4];
            for (int i = 0; i < 4; i++) {
                before[i] = FAAccess.amount(forge, FAForgeConversion.name(i));
                long after = (long) before[i] + reserved[i] - cost[i];
                if (after < 0 || after > FAAccess.capacity(forge, FAForgeConversion.name(i))) {
                    cancel(level, forge, state); return;
                }
            }
            state.putIntArray("before", before);
            state.putBoolean("normalized", true);
            forge.setChanged();
            try {
                for (int i = 0; i < 4; i++) FAAccess.amount(forge, FAForgeConversion.name(i), need[i]);
                var manager = FAAccess.call(forge, "getRitualManager");
                var selected = (java.util.Optional<?>) FAAccess.call(manager, "getValidRitual");
                var main = FAAccess.stack(forge, 4);
                var expected = ItemStack.parseOptional(level.registryAccess(), state.getCompound("output"));
                if (selected.isEmpty() || ((Holder<?>) selected.get()).value() != recipe.recipe()
                        || !ItemStack.matches(expected, FAAccess.ritualResult(recipe.recipe(), main))) {
                    cancel(level, forge, state); return;
                }
                FAAccess.call(manager, "startRitual", FakePlayerFactory.getMinecraft(level),
                        FAAccess.call(FAAccess.call(forge, "getEssenceManager"), "getStorage"));
            } catch (RuntimeException | LinkageError failure) {
                CompatibilityDiagnostics.failure(FAForgeRitualJobs.class, "native-start", failure);
            }
            if (!FAAccess.active(forge)) { cancel(level, forge, state); return; }
        }
        // Native start now owns the physical inputs. Never refund them after this point.
        state.putString("mode", "ritual");
        var before = state.getIntArray("before");
        for (int i = 0; i < 4; i++) {
            try { FAAccess.amount(forge, FAForgeConversion.name(i), (int) ((long) before[i] + reserved[i] - cost[i])); }
            catch (RuntimeException | LinkageError failure) {
                CompatibilityDiagnostics.failure(FAForgeRitualJobs.class, "settle-" + i, failure);
            }
        }
        forge.setChanged();
        TRACKED.remove(forge);
    }

    private static boolean targetsMatch(ServerLevel level, CompoundTag state) {
        var targets = state.getList("targets", 10);
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.getCompound(i);
            var pos = BlockPos.of(target.getLong("pos"));
            if (!level.isLoaded(pos)) return false;
            var be = level.getBlockEntity(pos);
            if (be == null || (i > 0 && !FAAccess.is(be, FAAccess.PEDESTAL))) return false;
            var item = ItemStack.parseOptional(level.registryAccess(), target.getCompound("item"));
            if (!ItemStack.matches(item, FAForgeAdapter.physical(be, i))) return false;
        }
        return !targets.isEmpty();
    }

    private static void cancel(ServerLevel level, BlockEntity forge, CompoundTag state) {
        state.putString("mode", "refunding");
        forge.setChanged();
        refund(level, forge, state);
    }

    private static void refund(ServerLevel level, BlockEntity forge, CompoundTag state) {
        if (state.getBoolean("normalized")) {
            var before = state.getIntArray("before");
            boolean restored = true;
            for (int i = 0; i < 4; i++) {
                try { FAAccess.amount(forge, FAForgeConversion.name(i), before[i]); }
                catch (RuntimeException | LinkageError failure) {
                    CompatibilityDiagnostics.failure(FAForgeRitualJobs.class, "restore-" + i, failure);
                }
                restored &= FAAccess.amount(forge, FAForgeConversion.name(i)) == before[i];
            }
            if (!restored) return;
            state.remove("normalized");
            forge.setChanged();
        }
        var refunds = state.getList("refund_items", 10);
        var targets = state.getList("targets", 10);
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.getCompound(i);
            if (target.getBoolean("released")) continue;
            var pos = BlockPos.of(target.getLong("pos"));
            if (!level.isLoaded(pos)) return;
            var be = level.getBlockEntity(pos);
            var item = ItemStack.parseOptional(level.registryAccess(), target.getCompound("item"));
            if (be != null && (i == 0 || FAAccess.is(be, FAAccess.PEDESTAL))
                    && ItemStack.matches(item, FAForgeAdapter.physical(be, i))) {
                try { FAForgeAdapter.setPhysical(be, i, ItemStack.EMPTY); }
                catch (RuntimeException | LinkageError failure) {
                    CompatibilityDiagnostics.failure(FAForgeRitualJobs.class, "remove-refund", failure);
                }
                if (!FAForgeAdapter.physical(be, i).isEmpty()) return;
                refunds.add(item.save(level.registryAccess()));
                state.put("refund_items", refunds);
            }
            target.putBoolean("released", true);
            forge.setChanged();
        }
        state.putString("mode", "refund");
        forge.setChanged();
        TRACKED.remove(forge);
    }

    static List<GenericStack> drainRefund(ServerLevel level, BlockEntity forge, CompoundTag state) {
        var out = new ArrayList<GenericStack>();
        for (var tag : state.getList("refund_items", 10)) {
            var stack = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) tag);
            if (!stack.isEmpty()) out.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
        }
        var reserved = state.getIntArray("reserved");
        for (int i = 0; i < reserved.length; i++) if (reserved[i] > 0)
            out.add(new GenericStack(ForbiddenEssenceResources.ALL.get(i), reserved[i]));
        forge.getPersistentData().remove(FAForgeAdapter.JOB);
        forge.setChanged();
        return List.copyOf(out);
    }
}
