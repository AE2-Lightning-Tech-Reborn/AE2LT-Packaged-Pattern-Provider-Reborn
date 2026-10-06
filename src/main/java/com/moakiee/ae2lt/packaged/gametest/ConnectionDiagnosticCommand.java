package com.moakiee.ae2lt.packaged.gametest;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

/**
 * Production front end for the adapter connection checks: an ops-only
 * {@code /ae2ltdiag connections} command that runs the same engine as the
 * dev-only Forge gametests ({@link AdapterConnectionTests}) — Forge disables
 * gametests on production servers, so this is the only way to verify the
 * adapter↔mod wiring on a real dedicated server.
 *
 * <p>Blocks are placed next to the world spawn (kept loaded), checked, and
 * restored to their original state. After recognition, each machine is also
 * run through idle {@code extractOutputs}/{@code bind}/{@code planWithBinding}
 * (conn41 on the co-located set). conn38 waits
 * {@link AdapterConnectionCheck#COLOCATED_SETTLE_TICKS} ticks before the
 * post-stress rescan so the command cannot PASS a failed second scan.
 */
@Mod.EventBusSubscriber(modid = AE2LTPackagedProvider.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConnectionDiagnosticCommand {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp-conn");
    private static final AtomicBoolean CONN38_PENDING = new AtomicBoolean(false);
    private static volatile PendingConn38 pendingConn38;

    private ConnectionDiagnosticCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2ltdiag")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("connections").executes(context ->
                        runConnections(context.getSource()))));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var pending = pendingConn38;
        if (pending == null) {
            return;
        }
        if (--pending.remainingTicks > 0) {
            return;
        }
        pendingConn38 = null;
        finishConn38(pending);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var pending = pendingConn38;
        pendingConn38 = null;
        if (pending != null) {
            AdapterConnectionCheck.removeColocated(pending.level, pending.state);
            CONN38_PENDING.set(false);
            LOG.warn("[ae2ltpp-conn] conn38 restored colocated blocks because the server is stopping");
        }
    }

    private static int runConnections(CommandSourceStack source) {
        var level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.literal(
                    "ae2ltdiag must run from the overworld (spawn chunks are kept loaded there)"));
            return 0;
        }
        if (!CONN38_PENDING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal(
                    "ae2ltdiag conn38 is still settling; wait and retry"));
            return 0;
        }

        boolean holdPending = false;
        try {
            var tally = new Tally();
            AdapterConnectionCheck.Sink sink = new AdapterConnectionCheck.Sink() {
                @Override
                public void begin(String name) {
                    LOG.info("[ae2ltpp-conn] BEGIN {}", name);
                }

                @Override
                public void pass(String name, String summary) {
                    tally.passed++;
                    LOG.info("[ae2ltpp-conn] PASS {} | {}", name, summary);
                }

                @Override
                public void fail(String name, String detail) {
                    tally.failed++;
                    LOG.error("[ae2ltpp-conn] FAIL {} | {}", name, detail);
                }

                @Override
                public void info(String name, String message) {
                    LOG.info("[ae2ltpp-conn] INFO {} | {}", name, message);
                }
            };

            var cases = AdapterConnectionCheck.cases();
            for (int i = 0; i < cases.size(); i++) {
                AdapterConnectionCheck.runCase(level, grid(level, i), cases.get(i), sink);
            }
            var negatives = AdapterConnectionCheck.negativeCases();
            for (int i = 0; i < negatives.size(); i++) {
                AdapterConnectionCheck.runNegative(level, grid(level, 40 + i), negatives.get(i), sink);
            }

            AdapterConnectionCheck.runForkSplit(level, sink);
            AdapterConnectionCheck.runInventory(level, sink);

            int machines = 0;
            boolean conn38Pending = false;
            var colocated = AdapterConnectionCheck.placeColocated(level, grid(level, 80), sink);
            if (colocated != null) {
                machines = colocated.positions().size();
                if (!AdapterConnectionCheck.stressColocated(level, colocated, sink)) {
                    AdapterConnectionCheck.removeColocated(level, colocated);
                } else {
                    conn38Pending = true;
                    holdPending = true;
                    pendingConn38 = new PendingConn38(level, colocated, sink, tally, source, machines,
                            AdapterConnectionCheck.COLOCATED_SETTLE_TICKS);
                }
            }

            String summary = conn38Pending
                    ? "ae2ltdiag: " + tally.passed + " passed, " + tally.failed
                    + " failed so far; conn38 settling "
                    + AdapterConnectionCheck.COLOCATED_SETTLE_TICKS
                    + " ticks — command not finished"
                    : "ae2ltdiag: " + tally.passed + " passed, " + tally.failed + " failed";
            LOG.info("[ae2ltpp-conn] SUMMARY pass={} fail={} colocated_machines={} conn38_pending={}",
                    tally.passed, tally.failed, machines, conn38Pending);
            source.sendSuccess(() -> Component.literal(summary), false);
            if (conn38Pending) {
                return 0;
            }
            return tally.failed == 0 ? Command.SINGLE_SUCCESS : 0;
        } finally {
            if (!holdPending) {
                CONN38_PENDING.set(false);
            }
        }
    }

    private static void finishConn38(PendingConn38 pending) {
        boolean scanPassed = false;
        boolean harvestPassed = false;
        boolean restored = false;
        try {
            boolean ok = AdapterConnectionCheck.scanColocated(pending.level, pending.state, pending.sink);
            if (ok) {
                harvestPassed = AdapterConnectionCheck.runDispatchHarvestColocated(
                        pending.level, pending.state, pending.sink);
                scanPassed = true;
            }
        } catch (RuntimeException | LinkageError e) {
            // A throw here must never reach the server tick loop — report it
            // through the sink so the tally (and the follow-up message) fail.
            LOG.error("[ae2ltpp-conn] conn38 delayed scan threw", e);
            pending.sink.fail("conn38_all_adapters_colocated", "delayed scan threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            try {
                AdapterConnectionCheck.removeColocated(pending.level, pending.state);
                restored = true;
            } catch (RuntimeException | LinkageError e) {
                LOG.error("[ae2ltpp-conn] conn38 restore threw", e);
                pending.sink.fail("conn38_all_adapters_colocated", "restore threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            CONN38_PENDING.set(false);
        }
        if (restored && scanPassed) {
            pending.sink.pass("conn38_all_adapters_colocated", pending.machines
                    + " machines co-located and routed correctly (" + AdapterConnectionCheck.colocatedExpectedRounds()
                    + " rounds + post-tick)");
        }
        if (restored && harvestPassed) {
            pending.sink.pass("conn41_idle_dispatch_harvest", pending.machines
                    + " idle machines refused unmatched bind/plan "
                    + "and did not vacuum unowned outputs");
        }
        String followUp = "ae2ltdiag finished: " + pending.tally.passed + " passed, "
                + pending.tally.failed + " failed";
        LOG.info("[ae2ltpp-conn] CONN38_DONE pass={} fail={}",
                pending.tally.passed, pending.tally.failed);
        if (pending.tally.failed == 0) {
            pending.source.sendSuccess(() -> Component.literal(followUp), false);
        } else {
            pending.source.sendFailure(Component.literal(followUp));
        }
    }

    /** Surface positions on a 2-block grid around the world spawn (loaded). */
    private static BlockPos grid(ServerLevel level, int slot) {
        var spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + 2 * (slot % 12) - 11;
        int z = spawn.getZ() + 2 * (slot / 12);
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

    private static final class Tally {
        int passed;
        int failed;
    }

    private static final class PendingConn38 {
        final ServerLevel level;
        final AdapterConnectionCheck.Colocated state;
        final AdapterConnectionCheck.Sink sink;
        final Tally tally;
        final CommandSourceStack source;
        final int machines;
        int remainingTicks;

        PendingConn38(ServerLevel level, AdapterConnectionCheck.Colocated state,
                      AdapterConnectionCheck.Sink sink, Tally tally,
                      CommandSourceStack source, int machines, int remainingTicks) {
            this.level = level;
            this.state = state;
            this.sink = sink;
            this.tally = tally;
            this.source = source;
            this.machines = machines;
            this.remainingTicks = remainingTicks;
        }
    }
}
