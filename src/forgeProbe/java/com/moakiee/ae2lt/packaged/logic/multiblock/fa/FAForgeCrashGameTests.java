package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import static com.moakiee.ae2lt.packaged.logic.multiblock.fa.ForgeProbeSupport.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in crash diagnosis: injected faults are labelled separately from natural failures. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class FAForgeCrashGameTests {
    private static final String FAULT = "FA_FORGE_INJECTED_FAULT";

    private static void fixture(GameTestHelper h, Consumer<Fixture> action) {
        var forge = build(h.getLevel(), h.absolutePos(new BlockPos(5, 3, 5)));
        h.runAfterDelay(100, () -> action.accept(ForgeProbeSupport.fixture(h.getLevel(), forge)));
    }

    private static void completed(GameTestHelper h, Fixture f, String scenario) {
        h.succeedWhen(() -> {
            h.assertTrue(!FAAccess.active(f.forge()), "wait for actual native ritual completion");
            h.assertTrue(f.collect().equals(expected()), "native output delivered once");
            h.assertTrue(f.collect().isEmpty(), "no second output receipt");
            trace(scenario, "complete", f.forge(), null);
        });
    }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void gateFaultBoundary(GameTestHelper h) {
        fixture(h, f -> {
            Throwable escaped = null;
            boolean accepted = false;
            try (var ignored = replaceField(f.forge(), "dataCache", null)) {
                try { accepted = f.adapter().canDispatch(f.level(), f.forge().getBlockPos(), null); }
                catch (RuntimeException | LinkageError e) { escaped = e; }
            } catch (Exception e) { throw new IllegalStateException(e); }
            trace("gate-fault", escaped == null ? "contained" : "escaped", f.forge(), escaped);
            h.assertTrue(escaped == null && !accepted, "gate rejects a broken cache without propagating exceptions");
            h.assertTrue(FAForgeAdapter.idle(f.forge()), "probe restores cache before native ticking");
            f.start();
            completed(h, f, "gate-fault-recovery");
        });
    }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void callbackFailureBeforeStartRollsBack(GameTestHelper h) { callback(h, false, false); }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void callbackLinkageErrorBeforeStartRollsBack(GameTestHelper h) { callback(h, false, true); }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void callbackFailureAfterStartConservesEssence(GameTestHelper h) { callback(h, true, false); }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void callbackLinkageErrorAfterStartConservesEssence(GameTestHelper h) { callback(h, true, true); }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void repeatedCallbackFailureAfterStartConservesEssence(GameTestHelper h) { callback(h, true, false, true); }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void repeatedCallbackLinkageErrorAfterStartConservesEssence(GameTestHelper h) { callback(h, true, true, true); }

    private static void callback(GameTestHelper h, boolean afterStart, boolean linkage) {
        callback(h, afterStart, linkage, false);
    }

    @SuppressWarnings("unchecked")
    private static void callback(GameTestHelper h, boolean afterStart, boolean linkage, boolean repeated) {
        fixture(h, f -> {
            var scenario = (afterStart ? "after-start-" : "before-start-") + (linkage ? "linkage" : "runtime")
                    + (repeated ? "-repeated" : "");
            var plan = f.plan();
            var manager = FAAccess.call(f.forge(), "getEssenceManager");
            var original = (Consumer<Object>) FAAccess.field(manager, "onChanged");
            var injected = new AtomicBoolean();
            Consumer<Object> callback = value -> {
                original.accept(value);
                if (FAAccess.active(f.forge()) == afterStart && (repeated || !injected.get())) {
                    injected.set(true);
                    trace(scenario, "inject", f.forge(), null);
                    if (linkage) throw new LinkageError(FAULT);
                    throw new IllegalStateException(FAULT);
                }
            };
            var retained = new ArrayList<GenericStack>();
            var restore = replaceField(manager, "onChanged", callback);
            h.assertTrue(DispatchExecutor.execute(plan, SOURCE, retained::addAll).success(), "pending job owns accepted inputs");
            h.assertTrue(!FAAccess.active(f.forge()), "native start is deferred");
            h.runAfterDelay(2, () -> {
                try { restore.close(); } catch (Exception e) { throw new IllegalStateException(e); }
                h.assertTrue(injected.get(), "requested fault boundary was actually exercised");
                trace(scenario, "deferred-start-returned", f.forge(), null);
                h.assertTrue(retained.isEmpty(), "no duplicate input refund after native ownership");
                if (afterStart) {
                    h.assertTrue(FAAccess.active(f.forge()), "committed ritual remains native-owned");
                    var expectedBalances = Arrays.stream(AFTER).boxed().toList();
                    h.assertTrue(balances(f.forge()).equals(expectedBalances),
                            "post-start callback must preserve all prior essences: expected "
                                    + expectedBalances + ", actual " + balances(f.forge()));
                } else {
                    h.assertTrue(!FAAccess.active(f.forge()), "failed deferred start never becomes active");
                    var returned = new appeng.api.stacks.KeyCounter();
                    f.collect().forEach(s -> returned.add(s.what(), s.amount()));
                    for (var input : f.inputs()) h.assertTrue(returned.get(input.what()) == input.amount(), "accepted input refunded exactly once");
                    h.assertTrue(f.collect().isEmpty() && FAForgeAdapter.idle(f.forge()), "failed job releases reservation once");
                    h.assertTrue(balances(f.forge()).equals(Arrays.stream(BEFORE).boxed().toList()),
                            "pre-start failure preserves all balances");
                    f.start();
                }
                completed(h, f, scenario);
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void nativeTickFaultBoundary(GameTestHelper h) {
        fixture(h, f -> {
            f.start();
            h.runAfterDelay(2, () -> {
                h.assertTrue(FAAccess.active(f.forge()), "ticker fault is injected after native start");
                var cache = FAAccess.field(f.forge(), "dataCache");
                var original = (List<?>) FAAccess.call(cache, "cachedIngredients");
                var faulting = new ArrayList<Object>(original) {
                    @Override public void forEach(Consumer<? super Object> consumer) {
                        throw new IllegalStateException(FAULT);
                    }
                };
                var injectedCache = FAAccess.construct(FAAccess.BASE + "common.block.entity.forge.ForgeDataCache",
                        faulting, FAAccess.call(cache, "mainIngredient"), FAAccess.call(cache, "enhancers"));
                var manager = FAAccess.call(f.forge(), "getRitualManager");
                Throwable escaped = null;
                try (var ignored = replaceField(manager, "dataCache", injectedCache)) {
                    // Invoke exactly the native ticker, catching outside it to keep the diagnostic server alive.
                    try {
                        FAAccess.call(FAAccess.type(FAAccess.FORGE), "serverTick", f.level(),
                                f.forge().getBlockPos(), f.forge().getBlockState(), f.forge());
                    } catch (RuntimeException | LinkageError e) { escaped = e; }
                } catch (Exception e) { throw new IllegalStateException(e); }
                trace("native-tick-fault", escaped == null ? "contained" : "escaped", f.forge(), escaped);
                h.assertTrue(escaped != null && escaped.getCause() != null
                        && FAULT.equals(escaped.getCause().getMessage()), "native ticker propagates injected failure");
                completed(h, f, "native-tick-fault-recovery");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void removingMainDuringRitual(GameTestHelper h) {
        fixture(h, f -> {
            f.start();
            h.runAfterDelay(10, () -> {
                trace("remove-main", "before", f.forge(), null);
                FAAccess.setStack(f.forge(), 4, ItemStack.EMPTY);
                interrupted(h, f, "remove-main");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void removingPedestalItemDuringRitual(GameTestHelper h) {
        fixture(h, f -> {
            f.start();
            h.runAfterDelay(10, () -> {
                var cache = FAAccess.field(f.forge(), "dataCache");
                var entry = ((List<?>) FAAccess.call(cache, "cachedIngredients")).getFirst();
                var pedestal = f.level().getBlockEntity((BlockPos) FAAccess.call(entry, "pos"));
                var trigger = FAAccess.field(FAAccess.type(FAAccess.BASE
                        + "common.block.pedestal.effect.PedestalEffectTrigger"), "PLAYER_REMOVE_ITEM");
                trace("remove-pedestal-item", "before", f.forge(), null);
                FAAccess.call(pedestal, "clearStack", null, trigger);
                interrupted(h, f, "remove-pedestal-item");
            });
        });
    }

    private static void interrupted(GameTestHelper h, Fixture f, String scenario) {
        h.assertTrue(!FAAccess.active(f.forge()), "native interruption stopped ritual");
        h.assertTrue(f.collect().isEmpty(), "interruption cannot fabricate promised output");
        h.assertTrue(!f.forge().getPersistentData().contains(FAForgeAdapter.JOB), "stale job is released");
        h.runAfterDelay(60, () -> {
            h.assertTrue(f.collect().isEmpty(), "no delayed or duplicate result after interruption");
            trace(scenario, "survived-native-ticks", f.forge(), null);
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void forgeReplacedBetweenPlanAndDispatch(GameTestHelper h) {
        fixture(h, f -> {
            var plan = f.plan();
            var pos = f.forge().getBlockPos();
            f.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            f.level().setBlockAndUpdate(pos, f.forge().getBlockState());
            var replacement = f.level().getBlockEntity(pos);
            h.assertTrue(replacement != f.forge(), "replacement has a distinct block entity");
            h.assertTrue(DispatchExecutor.execute(plan, SOURCE,
                    stacks -> h.fail("rejected simulation must leave inputs with CPU")).failure(),
                    "stale plan refuses removed target");
            h.assertTrue(FAForgeAdapter.idle(replacement) && f.collect().isEmpty(), "replacement stays untouched");
            trace("replace-before-dispatch", "rejected", replacement, null);
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void activeRitualReloadsIntoNewBlockEntity(GameTestHelper h) {
        fixture(h, f -> {
            f.start();
            h.runAfterDelay(10, () -> {
                trace("new-instance-reload", "before", f.forge(), null);
                var restored = reload(f.level(), f.forge());
                h.assertTrue(FAAccess.active(restored), "active ritual survives new-instance native load");
                trace("new-instance-reload", "after", restored, null);
                completed(h, new Fixture(f.level(), restored, f.adapter(), f.pattern(), f.inputs()),
                        "new-instance-reload");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=800)
    public static void pendingRitualReloadsAndStartsWithoutOutputPolling(GameTestHelper h) {
        fixture(h, f -> {
            long prepared = f.level().getGameTime();
            f.start();
            var restored = reload(f.level(), f.forge());
            h.assertTrue(!FAAccess.active(restored), "pending state is loaded before native start");
            h.runAfterDelay(3, () -> {
                h.assertTrue(FAAccess.active(restored), "event scheduler starts even when auto-return never polls");
                h.assertTrue(f.level().getGameTime() > prepared, "a distinct server tick owns native start");
                completed(h, new Fixture(f.level(), restored, f.adapter(), f.pattern(), f.inputs()), "pending-reload");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void replacedPendingMainRefundsOwnedInputsAndEssences(GameTestHelper h) {
        fixture(h, f -> {
            var list = new ArrayList<>(f.inputs());
            for (int i = 0; i < 4; i++) list.add(new GenericStack(
                    com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources.ALL.get(i), 1));
            var pattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                    appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(list, expected()), f.level());
            var binding = f.adapter().bind(f.level(), f.forge().getBlockPos(), pattern);
            h.assertTrue(binding != null, "explicit essence input pattern binds");
            var plan = f.adapter().planWithBinding(f.level(), f.forge().getBlockPos(), pattern,
                    counters(list), binding.handle(), SOURCE);
            h.assertTrue(DispatchExecutor.execute(plan, SOURCE, s -> h.fail("unexpected immediate refund")).success(), "pending inputs accepted");
            var manual = new ItemStack(net.minecraft.world.item.Items.DIAMOND);
            FAAccess.setStack(f.forge(), 4, manual);
            h.runAfterDelay(2, () -> {
                h.assertTrue(!FAAccess.active(f.forge()), "changed pending inputs cannot start");
                h.assertTrue(ItemStack.matches(FAAccess.stack(f.forge(), 4), manual), "manual replacement preserved");
                var returned = new appeng.api.stacks.KeyCounter();
                f.collect().forEach(s -> returned.add(s.what(), s.amount()));
                h.assertTrue(returned.get(f.inputs().getFirst().what()) == 0, "removed main item is never synthesized");
                for (var input : list.subList(1, list.size()))
                    h.assertTrue(returned.get(input.what()) == input.amount(), "owned material or reserved essence returned once");
                h.assertTrue(balances(f.forge()).equals(Arrays.stream(BEFORE).boxed().toList()), "pre-existing essences preserved");
                h.assertTrue(f.collect().isEmpty(), "no duplicate cancellation receipt");
                h.succeed();
            });
        });
    }
}
