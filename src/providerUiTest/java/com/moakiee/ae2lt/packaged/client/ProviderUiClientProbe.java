package com.moakiee.ae2lt.packaged.client;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import appeng.client.gui.AEBaseScreen;
import appeng.core.definitions.AEBlocks;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.api.client.PageNavigationButton;
import com.moakiee.ae2lt.api.client.TextureToolbarButton;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in client and packet-path regression in a disposable world. */
@EventBusSubscriber(modid = "ae2ltpp", value = Dist.CLIENT)
public final class ProviderUiClientProbe {
    private static final BlockPos EXTENDED = new BlockPos(0, 100, 0);
    private static final BlockPos NORMAL = EXTENDED.east(2);
    private static final BlockPos WIRELESS = EXTENDED.east(4);
    private static final BlockPos LT_EXTENDED = EXTENDED.east(6);
    private static int phase, ticks;
    private static boolean done;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 3000) { finish("FAIL: timeout phase=" + phase); return; }
        if (mc.player == null || mc.getSingleplayerServer() == null || ticks % 25 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("server checks", serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.getToasts().clear();
                    server(() -> {
                        var p = player();
                        var level = p.serverLevel();
                        // A repeat run must not inherit the previous run's persisted wireless mode/page.
                        for (var pos : List.of(EXTENDED, NORMAL, WIRELESS, LT_EXTENDED))
                            level.removeBlock(pos, false);
                        level.setBlockAndUpdate(EXTENDED, PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(NORMAL, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(WIRELESS, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(LT_EXTENDED, ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                        for (var pos : List.of(EXTENDED, NORMAL, WIRELESS, LT_EXTENDED))
                            level.setBlockAndUpdate(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        p.setGameMode(GameType.CREATIVE);
                        p.teleportTo(level, 2.5, 101, 3.5, Set.of(), 180, 25);
                    });
                }
                case 1 -> open(EXTENDED);
                case 2 -> {
                    expectPage(0, 4);
                    check(button().visible, "extended paging button visible");
                    checkToolbarState();
                    capture("extended-first-page.png");
                    click(0);
                }
                case 3 -> { expectPage(1, 4); click(1); }
                case 4 -> { expectPage(0, 4); click(1); }
                case 5 -> { expectPage(0, 4); scroll(-1); }
                case 6 -> { expectPage(1, 4); scroll(-1); }
                case 7 -> { expectPage(2, 4); click(0); }
                case 8 -> { expectPage(3, 4); click(0); }
                case 9 -> {
                    expectPage(3, 4);
                    capture("extended-last-page.png");
                    mc.options.guiScale().set(2);
                    mc.resizeDisplay();
                }
                case 10 -> {
                    expectPage(3, 4);
                    capture("extended-resized.png");
                    var mode = mc.screen.children().stream()
                            .filter(PackagedProviderTextureButton.class::isInstance)
                            .map(PackagedProviderTextureButton.class::cast)
                            .filter(b -> b.getTooltipMessage().contains(Component.translatable("ae2lt.gui.provider_mode.normal")))
                            .findFirst().orElseThrow();
                    mode.onPress();
                }
                case 11 -> {
                    expectPage(3, 4);
                    check(((PackagedPatternProviderMenu) mc.player.containerMenu).isWirelessMode(), "mode packet synchronized");
                    capture("extended-wireless.png");
                    open(NORMAL);
                }
                case 12 -> {
                    expectPage(0, 1);
                    check(!button().visible, "normal paging button hidden");
                    check(!((PackagedPatternProviderMenu) mc.player.containerMenu).isModeSwitchVisible(), "normal mode fixed");
                    capture("normal.png");
                    open(WIRELESS);
                }
                case 13 -> {
                    expectPage(0, 1);
                    check(!button().visible, "wireless paging button hidden");
                    check(((PackagedPatternProviderMenu) mc.player.containerMenu).isWirelessMode(), "wireless mode fixed");
                    capture("wireless.png");
                    open(LT_EXTENDED);
                }
                case 14 -> { expectPage(0, 4); check(button().visible, "LT paging button visible"); click(0); }
                case 15 -> { expectPage(1, 4); click(1); }
                case 16 -> { expectPage(0, 4); scroll(-1); }
                case 17 -> {
                    expectPage(1, 4);
                    capture("lt-shared-components.png");
                    finish("PASS: LTPP and LT shared widgets; native left/right clicks and wheel packets; page boundaries; "
                            + "all 144 slot positions and active states; resize; mode sync; single-page hiding; texture state and tooltips.");
                }
                default -> throw new AssertionError("phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish("FAIL phase=" + phase + ": " + failure);
        }
    }

    private static void checkToolbarState() {
        var button = PackagedProviderTextureButton.autoReturn(() -> {});
        check(button instanceof TextureToolbarButton, "shared texture renderer");
        List<Component> off = List.of(Component.literal("off"));
        List<Component> on = List.of(Component.literal("on"));
        button.setTooltipOff(off);
        button.setTooltipOn(on);
        button.setStateIndex(Integer.MAX_VALUE);
        check(button.getStateIndex() == 1 && button.getTooltipMessage().equals(on), "last state and tooltip clamp");
        button.setStateIndex(Integer.MIN_VALUE);
        check(button.getStateIndex() == 0 && button.getTooltipMessage().equals(off), "first state and tooltip clamp");
    }

    private static void expectPage(int expected, int total) {
        var menu = Minecraft.getInstance().player.containerMenu;
        check(page(menu) == expected, "client page at phase " + phase);
        var providerMenu = (appeng.menu.implementations.PatternProviderMenu) menu;
        var slots = providerMenu.getSlots(SlotSemantics.ENCODED_PATTERN);
        check(slots.size() == total * 36, "physical slot capacity");
        for (int i = 0; i < slots.size(); i++) {
            check(slots.get(i).isActive() == (i / 36 == expected), "slot activity " + i);
            check(slots.get(i).x == slots.get(i % 36).x && slots.get(i).y == slots.get(i % 36).y, "slot alignment " + i);
        }
        server(() -> check(page(player().containerMenu) == expected, "server page at phase " + phase));
    }

    private static int page(Object menu) {
        if (menu instanceof PackagedPatternProviderMenu m) return m.currentPage;
        if (menu instanceof OverloadedPatternProviderMenu m) return m.getCurrentPage();
        throw new AssertionError("unexpected menu " + menu);
    }

    private static void open(BlockPos pos) {
        server(() -> {
            var p = player();
            var be = p.serverLevel().getBlockEntity(pos);
            if (be instanceof PackagedPatternProviderBlockEntity host) host.openMenu(p, MenuLocators.forBlockEntity(host));
            else if (be instanceof OverloadedPatternProviderBlockEntity host) host.openMenu(p, MenuLocators.forBlockEntity(host));
            else throw new AssertionError("missing provider " + pos);
        });
    }

    private static PageNavigationButton button() {
        var buttons = Minecraft.getInstance().screen.children().stream().filter(PageNavigationButton.class::isInstance).toList();
        check(buttons.size() == 1, "exactly one shared page button after resize");
        return (PageNavigationButton) buttons.getFirst();
    }

    private static void click(int mouseButton) {
        var screen = Minecraft.getInstance().screen;
        var button = button();
        screen.mouseClicked(button.getX() + 8, button.getY() + 8, mouseButton);
        screen.mouseReleased(button.getX() + 8, button.getY() + 8, mouseButton);
    }

    private static void scroll(double delta) {
        var screen = (AEBaseScreen<?>) Minecraft.getInstance().screen;
        screen.mouseScrolled(screen.getGuiLeft() + 80, screen.getGuiTop() + 60, 0, delta);
    }

    private static void server(Runnable task) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { task.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static ServerPlayer player() {
        var mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static void finish(String result) {
        var mc = Minecraft.getInstance();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("provider-ui-result.txt"), result); }
        catch (Exception e) { e.printStackTrace(); }
        done = true;
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
