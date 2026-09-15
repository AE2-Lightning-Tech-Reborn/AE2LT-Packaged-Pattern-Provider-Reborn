package com.moakiee.ae2lt.packaged.gametest;

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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

/**
 * Production front end for the adapter connection checks: an ops-only
 * {@code /ae2ltdiag connections} command that runs the same engine as the
 * dev-only Forge gametests ({@link AdapterConnectionTests}) — Forge disables
 * gametests on release servers, so this is the only way to verify the
 * adapter↔mod wiring on a real dedicated server.
 *
 * <p>Blocks are placed next to the world spawn (kept loaded), checked, and
 * removed again within the same tick, so the world is left untouched.
 */
@Mod.EventBusSubscriber(modid = AE2LTPackagedProvider.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConnectionDiagnosticCommand {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp-conn");

    private ConnectionDiagnosticCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2ltdiag")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("connections").executes(context ->
                        runConnections(context.getSource()))));
    }

    private static int runConnections(CommandSourceStack source) {
        var level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.literal(
                    "ae2ltdiag must run from the overworld (spawn chunks are kept loaded there)"));
            return 0;
        }

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
        var colocated = AdapterConnectionCheck.placeColocated(level, grid(level, 80), sink);
        if (colocated != null) {
            machines = colocated.positions().size();
            if (AdapterConnectionCheck.stressColocated(level, colocated, sink)) {
                AdapterConnectionCheck.scanColocated(level, colocated, sink);
                sink.pass("conn38_all_adapters_colocated", machines
                        + " machines co-located and routed correctly (100 rounds)");
            }
            AdapterConnectionCheck.removeColocated(level, colocated);
        }

        String summary = "ae2ltdiag: " + tally.passed + " passed, " + tally.failed + " failed";
        LOG.info("[ae2ltpp-conn] SUMMARY pass={} fail={} colocated_machines={}",
                tally.passed, tally.failed, machines);
        source.sendSuccess(() -> Component.literal(summary), false);
        return tally.failed == 0 ? Command.SINGLE_SUCCESS : 0;
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
}
