package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import static com.moakiee.ae2lt.packaged.logic.multiblock.fa.ForgeProbeSupport.*;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives a real integrated server/client through native crafting, GUI sync and world rendering. */
@EventBusSubscriber(modid="ae2ltpp", value=Dist.CLIENT)
public final class FAForgeClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final boolean RELOAD = Boolean.parseBoolean(System.getProperty("ae2ltpp.forgeProbeReload", "true"));
    private static final boolean MANUAL = Boolean.getBoolean("ae2ltpp.forgeProbeManual");
    private static final boolean GUI = Boolean.parseBoolean(System.getProperty("ae2ltpp.forgeProbeGui", "true"));
    private static final boolean ABSENT = Boolean.getBoolean("ae2ltpp.forgeProbeAbsent");
    private static volatile Throwable serverError;
    private static volatile String serverPhase = "waiting";
    private static volatile int serverTicks;
    private static volatile boolean serverDone;
    // Server-thread-owned state; client only observes the volatile fields above.
    private static Fixture fixture;
    private static int setupTick, startTick;
    private static boolean reloaded;
    private static int clientTicks, phase, phaseTick, worldFrames, forgeFrames, mainFrames, ritualFrames, outputFrames;
    private static volatile boolean done;
    private static boolean observedClientActive;

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        serverTicks++;
        if (fixture != null && serverPhase.equals("waiting-start")) {
            if (FAAccess.active(fixture.forge())) {
                startTick = serverTicks;
                serverPhase = "active";
                trace("client-native-core", "started-next-tick", fixture.forge(), null);
            }
            return;
        }
        if (done || serverError != null || fixture == null || !serverPhase.equals("active")) return;
        try {
            int elapsed = serverTicks - startTick;
            if (RELOAD && !reloaded && elapsed >= 160) {
                trace("client-native-core", "before-new-instance-reload", fixture.forge(), null);
                var restored = reload(fixture.level(), fixture.forge());
                fixture = new Fixture(fixture.level(), restored, fixture.adapter(), fixture.pattern(), fixture.inputs());
                check(FAAccess.active(restored), "active ritual reloads into new entity");
                fixture.level().sendBlockUpdated(POS, restored.getBlockState(), restored.getBlockState(), 3);
                trace("client-native-core", "after-new-instance-reload", restored, null);
                reloaded = true;
            }
            if (!FAAccess.active(fixture.forge())) {
                var expected = ((appeng.api.stacks.AEItemKey) expected().getFirst().what()).toStack();
                check(net.minecraft.world.item.ItemStack.matches(FAAccess.stack(fixture.forge(), 4), expected),
                        "native core result physically exists with exact count and components");
                trace("client-native-core", "native-finished", fixture.forge(), null);
                serverPhase = "finished";
            }
            check(elapsed < 1400, "native ritual timed out");
        } catch (Throwable e) { serverError = e; }
    }

    @SubscribeEvent
    public static void worldRendered(RenderLevelStageEvent event) {
        if (done || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        worldFrames++;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var be = mc.level.getBlockEntity(POS);
        if (!FAAccess.is(be, FAAccess.FORGE)) return;
        if (event.getCamera().getPosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(POS)) > 32 * 32
                || !event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(POS).inflate(4))) return;
        forgeFrames++;
        if (!observedClientActive && FAAccess.field(FAAccess.call(be, "getRitualManager"), "activeRitualData") != null) {
            observedClientActive = true;
            trace("client-native-core", "client-received-active-ritual", be, null);
        }
        var visible = (net.minecraft.world.item.ItemStack) FAAccess.call(be, "getClientMainItem");
        if (visible.isEmpty()) return;
        mainFrames++;
        if (FAAccess.call(FAAccess.call(be, "getMagicCircleController"), "getMagicCircle") != null)
            ritualFrames++;
        if (visible.is(com.moakiee.ae2lt.packaged.registry.PPItems.FA_FORGE_ADAPTER.get()))
            outputFrames++;
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (done || ++clientTicks % 5 != 0) return;
        var mc = Minecraft.getInstance();
        try {
            check(clientTicks < 3600, "client timeout at phase " + phase + ", server=" + serverPhase);
            if (serverError != null) throw new AssertionError("integrated-server probe failed", serverError);
            if (phase > 0 && mc.getSingleplayerServer() == null)
                throw new AssertionError("client disconnected at phase " + phase + ", server=" + serverPhase);
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            switch (phase) {
                case 0 -> {
                    if (ABSENT) {
                        check(!net.neoforged.fml.ModList.get().isLoaded("forbidden_arcanus"), "FA must be absent in this probe");
                        finish("PASS: optional FA absent; real client joined integrated server without target-class errors");
                        return;
                    }
                    mc.getWindow().setTitle("FA forge crash probe - disposable world");
                    mc.options.pauseOnLostFocus = false;
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.getToasts().clear();
                    onServer(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setDayTime(6000);
                        var forge = build(level, POS);
                        // A solid viewing platform also handles ability packets racing initial login.
                        for (int x = -1; x <= 1; x++) for (int z = 5; z <= 7; z++)
                            level.setBlockAndUpdate(POS.offset(x, 0, z),
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        // Keep setup on the server, including the player's actual viewing position.
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, 0.5, 101, 6.5, Set.of(), 180, 12);
                        setupTick = serverTicks;
                        serverPhase = "built";
                        trace("client-native-core", "built", forge, null);
                    });
                    next();
                }
                case 1 -> {
                    if (serverPhase.equals("manual-prepared")) {
                        if (serverTicks - setupTick < 20) return;
                        onServer(() -> {
                            startManual();
                            startTick = serverTicks;
                            serverPhase = "active";
                            trace("client-native-core", "manual-gavel-started", fixture.forge(), null);
                        });
                        next();
                        return;
                    }
                    if (!serverPhase.equals("built") || serverTicks - setupTick < 110) return;
                    check(mc.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(POS)) < 32 * 32,
                            "camera remains near the forge: " + mc.player.position());
                    if (!FAAccess.is(mc.level.getBlockEntity(POS), FAAccess.FORGE)) return;
                    if (forgeFrames < 20) return;
                    onServer(() -> {
                        fixture = ForgeProbeSupport.fixture(mc.getSingleplayerServer().overworld(),
                                mc.getSingleplayerServer().overworld().getBlockEntity(POS));
                        if (MANUAL) {
                            prepareManual();
                            setupTick = serverTicks;
                            serverPhase = "manual-prepared";
                            trace("client-native-core", "manual-materials-placed", fixture.forge(), null);
                            return;
                        }
                        fixture.start();
                        if (RELOAD) {
                            trace("client-native-core", "before-pending-reload", fixture.forge(), null);
                            var restored = reload(fixture.level(), fixture.forge());
                            fixture = new Fixture(fixture.level(), restored, fixture.adapter(), fixture.pattern(), fixture.inputs());
                            check(!FAAccess.active(restored), "pending reload must remain inactive");
                            fixture.level().sendBlockUpdated(POS, restored.getBlockState(), restored.getBlockState(), 3);
                            reloaded = true;
                            trace("client-native-core", "after-pending-reload", restored, null);
                        }
                        startTick = serverTicks;
                        serverPhase = "waiting-start";
                        trace("client-native-core", "inputs-reserved", fixture.forge(), null);
                    });
                    if (!MANUAL) next();
                }
                case 2 -> {
                    if (!serverPhase.equals("active") || clientTicks - phaseTick < 70) return;
                    if (ritualFrames < 20 && clientTicks - phaseTick < 250) return;
                    capture("01-active-ritual.png");
                    check(ritualFrames >= 20, "real world frames with native main item and magic circle: world="
                            + worldFrames + ", forge=" + forgeFrames + ", main=" + mainFrames + ", ritual=" + ritualFrames);
                    if (!GUI) { phase = 4; phaseTick = clientTicks; return; }
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.openMenu((MenuProvider) fixture.forge());
                    });
                    next();
                }
                case 3 -> {
                    if (clientTicks - phaseTick < 30) return;
                    check(mc.screen != null && mc.screen.getClass().getName().contains("HephaestusForge"),
                            "native forge GUI opened: " + (mc.screen == null ? "null" : mc.screen.getClass()));
                    capture("02-native-forge-gui.png");
                    trace("client-native-core", "gui-rendered", null, null);
                    mc.player.closeContainer();
                    next();
                }
                case 4 -> {
                    if (!serverPhase.equals("finished") || outputFrames < 20) return;
                    capture("03-native-core-output.png");
                    onServer(() -> {
                        check(!RELOAD || reloaded, "requested new-instance reload was exercised");
                        check(MANUAL || !observedClientActive, "input packets must not contain the later active ritual");
                        check(balances(fixture.forge()).equals(Arrays.stream(AFTER).boxed().toList()),
                                "normal native craft preserves surplus essences");
                        if (MANUAL) {
                            check(!fixture.forge().getPersistentData().contains(FAForgeAdapter.JOB), "manual craft never owned by addon");
                            FAAccess.setStack(fixture.forge(), 4, net.minecraft.world.item.ItemStack.EMPTY);
                        } else check(fixture.collect().equals(expected()), "collect actual output once");
                        check(fixture.collect().isEmpty(), "no duplicate output");
                        trace("client-native-core", "collected", fixture.forge(), null);
                        serverDone = true;
                    });
                    next();
                }
                case 5 -> {
                    if (!serverDone || clientTicks - phaseTick < 30) return;
                    var clientForge = mc.level.getBlockEntity(POS);
                    check(((net.minecraft.world.item.ItemStack) FAAccess.call(clientForge, "getClientMainItem")).isEmpty(),
                            "client observes output removal");
                    finish("PASS: real native ritual, particles and item rendering; "
                            + "result rendering and single collection; manual=" + MANUAL + ", gui=" + GUI + ", reload=" + RELOAD
                            + ", ritualFrames=" + ritualFrames + ", outputFrames=" + outputFrames);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            finish("FAIL: phase=" + phase + ", server=" + serverPhase + ", " + e);
        }
    }

    private static void next() { phase++; phaseTick = clientTicks; }

    private static void prepareManual() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = fixture.level();
        var forge = fixture.forge();
        FAAccess.setStack(forge, 4, ((appeng.api.stacks.AEItemKey) fixture.inputs().getFirst().what()).toStack());
        var trigger = FAAccess.field(FAAccess.type(FAAccess.BASE
                + "common.block.pedestal.effect.PedestalEffectTrigger"), "PLAYER_PLACE_ITEM");
        var positions = java.util.List.of(POS.offset(-3, 0, 0), POS.offset(3, 0, 0), POS.offset(0, 0, 3));
        int index = 0;
        for (var input : fixture.inputs().subList(1, fixture.inputs().size())) {
            for (long i = 0; i < input.amount(); i++) {
                FAAccess.call(level.getBlockEntity(positions.get(index++)), "setStack",
                        ((appeng.api.stacks.AEItemKey) input.what()).toStack(), player, trigger);
            }
        }
    }

    private static void startManual() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = fixture.level();
        var forge = fixture.forge();
        var gavel = ((appeng.api.stacks.AEItemKey) stack("forbidden_arcanus:wooden_blacksmith_gavel", 1).what()).toStack();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gavel);
        FAAccess.call(forge.getBlockState().getBlock(), "updateState", forge.getBlockState(), level, POS);
        var result = FAAccess.call(forge.getBlockState().getBlock(), "useItemOn", gavel, forge.getBlockState(),
                level, POS, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS),
                        net.minecraft.core.Direction.UP, POS, false));
        check(result == net.minecraft.world.ItemInteractionResult.SUCCESS
                || result == net.minecraft.world.ItemInteractionResult.CONSUME, "native gavel interaction handled: " + result);
        check(FAAccess.active(forge), "real player gavel starts native ritual");
    }

    private static void onServer(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); }
            catch (Throwable e) { serverError = e; }
        });
    }

    private static void capture(String name) throws java.io.IOException {
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static void finish(String result) {
        done = true;
        var mc = Minecraft.getInstance();
        try {
            if (result.startsWith("FAIL")) capture("failure.png");
            Files.writeString(mc.gameDirectory.toPath().resolve("forge-client-result.txt"), result);
        }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
        System.out.println("FA_FORGE_CLIENT_PROBE " + result);
        mc.stop();
    }
}
