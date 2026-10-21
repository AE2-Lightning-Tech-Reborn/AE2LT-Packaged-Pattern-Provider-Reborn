package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Forge 2.2.6 input contracts. Only the native server tick converts items. */
public final class FAForgeConversion {
    public static final String JOB = "ae2ltpp_forge_job";
    private static final String CLAIM = "fa:conversion@";
    public record Input(int index, AEItemKey item, int count, int min, int max, ItemStack remainder) {}
    public record Match(List<Input> inputs) {}
    private FAForgeConversion() {}
    public static Match match(Level level, IPatternDetails pattern) { return match(level, pattern, FAPatterns.declared(pattern)); }
    public static Match match(Level level, IPatternDetails pattern, KeyCounter[] inputs) { return match(level, pattern, FAPatterns.actual(inputs)); }
    private static Match match(Level level, IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null) return null;
        var inputs = new ArrayList<Input>();
        var expected = new KeyCounter();
        var occupied = new HashSet<Integer>();
        for (var stack : FAPatterns.stacks(amounts)) {
            if (!(stack.what() instanceof AEItemKey item) || stack.amount() > 64) return null;
            var input = describe(level, item, (int) stack.amount());
            if (input == null || !occupied.add(input.index())) return null;
            inputs.add(input);
            expected.add(ForbiddenEssenceResources.ALL.get(input.index()), input.min());
            if (!input.remainder().isEmpty()) expected.add(AEItemKey.of(input.remainder()), input.remainder().getCount());
        }
        if (inputs.isEmpty()) return null;
        var outputs = new KeyCounter();
        for (var output : pattern.getOutputs()) {
            if (output.amount() <= 0) return null;
            try { outputs.set(output.what(), Math.addExact(outputs.get(output.what()), output.amount())); }
            catch (ArithmeticException e) { return null; }
        }
        if (expected.size() != outputs.size()) return null;
        for (var entry : expected) if (outputs.get(entry.getKey()) != entry.getLongValue()) return null;
        return new Match(List.copyOf(inputs));
    }
    public static Input describe(Level level, AEItemKey item, int count) {
        if (count < 1 || count > item.toStack().getMaxStackSize()) return null;
        for (int i = 0; i < 4; i++) {
            var essence = FAAccess.essence(name(i));
            for (var nativeInput : FAAccess.registry(level, "hephaestus_forge/input")) {
                if (!Boolean.TRUE.equals(FAAccess.call(nativeInput, "canInput", essence, item.toStack()))) continue;
                String kind = nativeInput.getClass().getSimpleName();
                int min, max;
                ItemStack remainder = ItemStack.EMPTY;
                if (kind.equals("ItemInput")) {
                    int value = (int) FAAccess.call(nativeInput, "getInputValue", essence, item.toStack(), RandomSource.create(0));
                    min = max = Math.multiplyExact(value, count);
                } else if (kind.equals("BloodInput")) {
                    if (count != 1) return null;
                    // Native tick drains the whole tube in successive 10-point steps.
                    var type = FAAccess.type("com.stal111.forbidden_arcanus.common.item.BloodTestTubeItem");
                    min = max = (int) FAAccess.call(type, "getBlood", item.toStack());
                    remainder = (ItemStack) FAAccess.call(type, "removeBlood", item.toStack(), min);
                } else if (kind.equals("ExtractEnchantmentsInput")) {
                    if (count != 1) return null;
                    int xp = (int) FAAccess.call(nativeInput, "getEnchantmentXp", item.toStack());
                    min = (xp + 1) / 2; max = Math.subtractExact(Math.multiplyExact(min, 2), 1);
                    remainder = (ItemStack) FAAccess.call(FAAccess.type("net.valhelsia.valhelsia_core.api.common.util.ItemStackUtils"), "removeEnchantments", item.toStack());
                } else return null;
                return min > 0 && max >= min ? new Input(i, item, count, min, max, remainder) : null;
            }
        }
        return null;
    }
    public static boolean hasClaim(ServerLevel level, BlockPos pos, AdapterPersistentScope scope) {
        return scope.getState(pos, CLAIM + level.dimension().location()) != null;
    }
    private static String name(int index) { return ForbiddenEssenceResources.ALL.get(index).getId().getPath(); }
    public static void releaseAbandoned(BlockEntity forge) {
        if (!(forge.getLevel() instanceof ServerLevel level) || !FAAccess.live(forge)) return;
        var data = forge.getPersistentData();
        if (!data.contains(JOB)) return;
        var job = data.getCompound(JOB);
        var dimension = net.minecraft.resources.ResourceLocation.tryParse(job.getString("owner_dimension"));
        if (dimension == null || !job.contains("owner_pos") || !job.hasUUID("nonce")) return;
        var ownerLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        var ownerPos = BlockPos.of(job.getLong("owner_pos"));
        if (ownerLevel == null || !ownerLevel.isLoaded(ownerPos)) return;
        var owner = ownerLevel.getBlockEntity(ownerPos);
        if (owner instanceof AdapterPersistentScope scope) {
            var encoded = scope.getState(forge.getBlockPos(), CLAIM + level.dimension().location());
            if (encoded != null) {
                try { if (job.getUUID("nonce").equals(TagParser.parseTag(encoded).getUUID("nonce"))) return; }
                catch (Exception malformed) { return; }
            }
        }
        data.remove(JOB); forge.setChanged();
    }
    public static boolean idle(BlockEntity forge) {
        releaseAbandoned(forge);
        if (!FAAccess.live(forge) || FAAccess.active(forge) || forge.getPersistentData().contains(JOB)
                || !forge.getPersistentData().getString("ae2ltpp_hephaestus_pending_nonce").isEmpty()
                || !forge.getPersistentData().getString("ae2ltpp_hephaestus_completed_nonce").isEmpty()) return false;
        for (int slot = 4; slot < 9; slot++) if (!FAAccess.stack(forge, slot).isEmpty()) return false;
        return true;
    }
    public static DispatchPlan plan(ServerLevel level, BlockEntity forge, Match match, AdapterPersistentScope scope) {
        if (!(scope instanceof BlockEntity owner) || owner.getLevel() == null || !idle(forge)) return null;
        var targets = new ArrayList<TargetSlot>();
        var state = new CompoundTag();
        state.putUUID("nonce", UUID.randomUUID());
        state.putString("owner_dimension", owner.getLevel().dimension().location().toString());
        state.putLong("owner_pos", owner.getBlockPos().asLong());
        var jobs = new ListTag();
        for (var input : match.inputs()) {
            int baseline = FAAccess.amount(forge, name(input.index()));
            if ((long) baseline + input.max() > FAAccess.capacity(forge, name(input.index()))) return null;
            int slot = 5 + input.index();
            targets.add(new TargetSlot(level, forge.getBlockPos(), null, List.of(new GenericStack(input.item(), input.count())), InsertionStrategy.CUSTOM,
                    (stack, mode) -> {
                        if (!FAAccess.live(forge) || !stack.what().equals(input.item()) || stack.amount() != input.count()
                                || !FAAccess.stack(forge, slot).isEmpty()) return 0L;
                        if (mode == Actionable.MODULATE) FAAccess.setStack(forge, slot, input.item().toStack(input.count()));
                        return stack.amount();
                    }));
            var job = new CompoundTag(); job.putInt("index", input.index()); job.putInt("baseline", baseline);
            job.putInt("min", input.min()); job.putInt("max", input.max());
            job.put("remainder", input.remainder().save(new CompoundTag())); jobs.add(job);
        }
        state.put("inputs", jobs);
        return new DispatchPlan(targets, () -> save(level, forge, state, scope), (accepted, sink) -> {
            for (var receipt : accepted) {
                int i = targets.indexOf(receipt.target()); if (i < 0) continue;
                int slot = 5 + match.inputs().get(i).index();
                var current = FAAccess.stack(forge, slot);
                if (receipt.stack().what().equals(AEItemKey.of(current)) && current.getCount() == receipt.stack().amount()) {
                    FAAccess.setStack(forge, slot, ItemStack.EMPTY); sink.accept(receipt.stack());
                }
            }
            forge.getPersistentData().remove(JOB);
            scope.clearState(forge.getBlockPos(), CLAIM + level.dimension().location());
        });
    }
    private static void save(ServerLevel level, BlockEntity forge, CompoundTag state, AdapterPersistentScope scope) {
        scope.setState(forge.getBlockPos(), CLAIM + level.dimension().location(), state.toString());
        forge.getPersistentData().put(JOB, state.copy()); forge.setChanged();
    }
    public static List<GenericStack> collect(ServerLevel level, BlockPos pos, AdapterPersistentScope scope, AllowedOutputFilter filter) {
        var forge = level.getBlockEntity(pos);
        if (!FAAccess.live(forge) || FAAccess.active(forge)) return List.of();
        var encoded = scope.getState(pos, CLAIM + level.dimension().location());
        if (encoded == null) return List.of();
        CompoundTag state;
        try { state = TagParser.parseTag(encoded); } catch (Exception e) { return List.of(); }
        var machineState = forge.getPersistentData().getCompound(JOB);
        if (!state.hasUUID("nonce") || !machineState.hasUUID("nonce") || !state.getUUID("nonce").equals(machineState.getUUID("nonce"))) return List.of();
        var jobs = state.getList("inputs", Tag.TAG_COMPOUND);
        if (jobs.isEmpty() || jobs.size() > 4) return List.of();
        for (int i = 0; i < jobs.size(); i++) {
            var job = jobs.getCompound(i); int index = job.getInt("index");
            if (index < 0 || index > 3) return List.of();
            if (job.getBoolean("done")) continue;
            var expected = ItemStack.of(job.getCompound("remainder"));
            if (!ItemStack.matches(expected, FAAccess.stack(forge, 5 + index))) return List.of();
            var key = ForbiddenEssenceResources.ALL.get(index);
            if (!job.getBoolean("resource_done")) {
                int before = FAAccess.amount(forge, name(index));
                int amount = Math.min(job.getInt("max"), before - job.getInt("baseline"));
                if (amount < job.getInt("min") || !filter.matches(key)) return List.of();
                try { FAAccess.amount(forge, name(index), before - amount); }
                catch (RuntimeException failure) { CompatibilityDiagnostics.failure(FAForgeConversion.class, "collectEssence", failure); }
                int moved = Math.max(0, Math.min(amount, before - FAAccess.amount(forge, name(index))));
                if (moved == 0) return List.of();
                // Persist the debit before returning its receipt. Partial native mutations
                // leave only the uncollected difference available on the next tick.
                job.putInt("min", Math.max(0, job.getInt("min") - moved));
                job.putInt("max", job.getInt("max") - moved);
                if (moved == amount) job.putBoolean("resource_done", true);
                if (expected.isEmpty() && job.getBoolean("resource_done")) job.putBoolean("done", true);
                save(level, forge, state, scope);
                return List.of(new GenericStack(key, moved));
            }
            if (!expected.isEmpty()) {
                if (!filter.matches(AEItemKey.of(expected))) return List.of();
                FAAccess.setStack(forge, 5 + index, ItemStack.EMPTY);
                job.putBoolean("done", true); save(level, forge, state, scope);
                return List.of(new GenericStack(AEItemKey.of(expected), expected.getCount()));
            }
        }
        forge.getPersistentData().remove(JOB); forge.setChanged();
        scope.clearState(pos, CLAIM + level.dimension().location());
        return List.of();
    }
}
