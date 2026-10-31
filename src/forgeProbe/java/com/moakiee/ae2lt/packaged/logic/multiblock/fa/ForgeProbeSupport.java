package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.google.gson.Gson;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Test-only fixtures and append-only breadcrumbs, shared by server and real-client probes. */
public final class ForgeProbeSupport {
    public static final IActionSource SOURCE = IActionSource.empty();
    public static final int[] BEFORE = {25, 3, 130, 17};
    public static final int[] AFTER = {5, 2, 30, 7};

    private ForgeProbeSupport() {}

    public record Fixture(ServerLevel level, BlockEntity forge, FAForgeAdapter adapter,
                          IPatternDetails pattern, List<GenericStack> inputs) {
        public DispatchPlan plan() {
            var binding = adapter.bind(level, forge.getBlockPos(), pattern);
            check(binding != null, "native core ritual must bind");
            var plan = adapter.planWithBinding(level, forge.getBlockPos(), pattern,
                    counters(inputs), binding.handle(), SOURCE);
            check(plan != null, "native core ritual must plan");
            return plan;
        }

        public void start() {
            check(DispatchExecutor.execute(plan(), SOURCE,
                    stacks -> { throw new AssertionError("unexpected retained inputs: " + stacks); }).success(),
                    "native ritual dispatch");
            check(!FAAccess.active(forge) && forge.getPersistentData().getCompound(FAForgeAdapter.JOB)
                    .getString("mode").equals("waiting"), "dispatch reserves inputs without starting in the same tick");
        }

        public List<GenericStack> collect() {
            return adapter.extractOutputs(level, forge.getBlockPos(), new AllowedOutputFilter(), SOURCE);
        }
    }

    public static BlockEntity build(ServerLevel level, BlockPos pos) {
        check(ModList.get().isLoaded("forbidden_arcanus"), "FA is required; absence is not a pass");
        // Re-running the disposable client world must not reuse the previous active forge or pedestals.
        level.removeBlock(pos, false);
        for (var offset : List.of(new BlockPos(-3, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, 0, 3)))
            level.removeBlock(pos.offset(offset), false);
        String[] rows = {"***PPP***", "*PPPAPPP*", "*PAPPPAP*", "PPPPCPPPP", "PAPCACPAP",
                "PPPPCPPPP", "*PAPPPAP*", "*PPPAPPP*", "***PPP***"};
        for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) {
            String path = switch (rows[z].charAt(x)) {
                case 'P' -> "polished_darkstone";
                case 'A' -> "gilded_chiseled_polished_darkstone";
                case 'C' -> "chiseled_arcane_polished_darkstone";
                default -> null;
            };
            if (path != null) block(level, pos.offset(x - 4, -1, z - 4), path);
        }
        var forge = block(level, pos, "hephaestus_forge_tier_1");
        for (var offset : List.of(new BlockPos(-3, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, 0, 3)))
            block(level, pos.offset(offset), "darkstone_pedestal");
        return forge;
    }

    private static BlockEntity block(ServerLevel level, BlockPos pos, String path) {
        var id = ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path);
        check(BuiltInRegistries.BLOCK.containsKey(id), "native block exists: " + id);
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id).defaultBlockState());
        return level.getBlockEntity(pos);
    }

    public static Fixture fixture(ServerLevel level, BlockEntity forge) {
        var items = List.of(new GenericStack(AEItemKey.of(PPItems.BASIC_PACKAGED_CORE.get()), 1),
                stack("forbidden_arcanus:deorum_ingot", 2), stack("forbidden_arcanus:dark_matter", 1));
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                items, expected()), level);
        for (int i = 0; i < 4; i++) FAAccess.amount(forge, FAForgeConversion.name(i), BEFORE[i]);
        check(FAAccess.structure(level, forge.getBlockPos()), "native base pattern is present");
        return new Fixture(level, forge, new FAForgeAdapter(), pattern, items);
    }

    public static GenericStack stack(String id, long count) {
        var itemId = ResourceLocation.parse(id);
        check(BuiltInRegistries.ITEM.containsKey(itemId), "native item exists: " + itemId);
        return new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(itemId)), count);
    }

    public static KeyCounter[] counters(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        stacks.forEach(stack -> counter.add(stack.what(), stack.amount()));
        return new KeyCounter[] {counter};
    }

    public static List<GenericStack> expected() {
        return List.of(new GenericStack(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get()), 1));
    }

    public static List<Integer> balances(BlockEntity forge) {
        var values = new ArrayList<Integer>();
        for (int i = 0; i < 4; i++) values.add(FAAccess.amount(forge, FAForgeConversion.name(i)));
        return values;
    }

    public static BlockEntity reload(ServerLevel level, BlockEntity old) {
        var tag = old.saveWithFullMetadata(level.registryAccess());
        var pos = old.getBlockPos();
        var state = old.getBlockState();
        level.removeBlockEntity(pos);
        // Match disk loading: decode into a NEW entity before attaching it to the world.
        var restored = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        check(restored != null && restored != old, "new block entity decoded from native NBT");
        level.setBlockEntity(restored);
        check(FAAccess.live(restored) && !FAAccess.live(old), "only the new entity is live");
        return restored;
    }

    public static AutoCloseable replaceField(Object owner, String name, Object replacement) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            var original = field.get(owner);
            field.set(owner, replacement);
            return () -> field.set(owner, original);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Probe injection failed: " + name, e);
        }
    }

    public static synchronized void trace(String scenario, String phase, BlockEntity forge, Throwable failure) {
        var row = new LinkedHashMap<String, Object>();
        row.put("scenario", scenario);
        row.put("phase", phase);
        row.put("thread", Thread.currentThread().getName());
        if (forge != null) {
            row.put("pos", forge.getBlockPos().toShortString());
            row.put("live", FAAccess.live(forge));
            row.put("active", FAAccess.active(forge));
            row.put("job", forge.getPersistentData().contains(FAForgeAdapter.JOB));
            row.put("essences", balances(forge));
            row.put("main", FAAccess.stack(forge, 4).toString());
        }
        if (failure != null) {
            row.put("exception", failure.toString());
            while (failure.getCause() != null) failure = failure.getCause();
            row.put("rootCause", failure.toString());
        }
        String json = new Gson().toJson(row);
        System.out.println("FA_FORGE_PROBE " + json);
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("forge-probe.jsonl"), json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not persist probe breadcrumb", e);
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
