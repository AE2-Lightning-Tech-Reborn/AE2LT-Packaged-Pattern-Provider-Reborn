package com.moakiee.ae2lt.packaged.client;

import java.nio.file.Files;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.menu.CoreOrderConfirmation;
import com.moakiee.ae2lt.packaged.mixin.client.CraftConfirmScreenAccessor;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Tests the transformed constructor, real AE2 GUI synchronization and button restoration. */
@EventBusSubscriber(modid = "ae2ltpp", value = Dist.CLIENT)
public final class CoreConfirmationClientProbe {
    private static int ticks;
    private static boolean done;
    private static CraftConfirmScreen screen;
    private static Component originalLabel;
    private static int originalX, originalWidth;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 2400) { finish("FAIL: client timeout"); return; }
        if (mc.player == null || mc.level == null || ticks % 20 != 0) return;
        try {
            if (screen == null) {
                var menu = newMenu();
                var state = (CoreOrderConfirmation) menu;
                check("".equals(state.ae2ltpp$coreMode()), "fresh menu mode must be empty before any sync; actual=" + state.ae2ltpp$coreMode());
                check(Component.empty().equals(state.ae2ltpp$coreDescription()), "fresh menu description");
                check(!state.ae2ltpp$coreBlocked(), "fresh menu is not blocked");
                screen = new CraftConfirmScreen(menu, mc.player.getInventory(), Component.literal("Core confirmation QA"),
                        StyleManager.loadStyleDoc("/screens/craft_confirm.json"));
                mc.setScreen(screen);
                var button = ((CraftConfirmScreenAccessor) screen).ae2ltpp$startButton();
                originalLabel = button.getMessage();
                originalX = button.getX();
                originalWidth = button.getWidth();
                return; // Allow the normal render loop to exercise a fresh, unsynchronized menu.
            }
            check(mc.screen == screen, "fresh confirmation remains open after rendering");
            var menu = screen.getMenu();
            var state = (CoreOrderConfirmation) menu;
            var button = ((CraftConfirmScreenAccessor) screen).ae2ltpp$startButton();
            var originalTooltip = button.getTooltip();
            var sender = newMenu();
            var senderSync = new DataSynchronization(sender);
            var receiverSync = new DataSynchronization(menu);

            // Serialize a newly created menu before the first server broadcast as well.
            sync(senderSync, receiverSync, true);
            check("".equals(state.ae2ltpp$coreMode()), "empty default survives full GUI sync");
            CoreOrderConfirmationUi.update(screen);
            check(button.getMessage().equals(originalLabel), "ordinary crafting keeps its label");

            set(sender, "coreMode", "install");
            var description = Component.literal("Install at 0, 100, 0");
            set(sender, "coreDescription", description);
            sync(senderSync, receiverSync, false);
            check("install".equals(state.ae2ltpp$coreMode()), "mode decoded by AE2 GUI sync");
            check(description.equals(state.ae2ltpp$coreDescription()), "description decoded by AE2 GUI sync");
            button.active = true;
            CoreOrderConfirmationUi.update(screen);
            check(button.getMessage().equals(Component.translatable("ae2ltpp.terminal.install")), "install label");
            check(button.active && button.getTooltip() != null, "install button and tooltip");
            check(button.getX() + button.getWidth() == originalX + originalWidth, "button right edge is preserved");

            set(sender, "coreMode", "busy");
            set(sender, "coreBlocked", true);
            set(sender, "coreDescription", null); // Component GUI fields explicitly allow null.
            sync(senderSync, receiverSync, false);
            check(state.ae2ltpp$coreDescription() == null, "nullable description decoded");
            CoreOrderConfirmationUi.update(screen);
            check(!button.active, "blocked order disables the start button with a null description");

            set(menu, "coreMode", null); // Reproduce a missing initializer / legacy transient state.
            CoreOrderConfirmationUi.update(screen);
            check(button.getMessage().equals(originalLabel), "null mode restores native label");
            check(button.getX() == originalX && button.getWidth() == originalWidth, "null mode restores geometry");
            check(button.getTooltip() == originalTooltip, "null mode restores native tooltip");

            set(sender, "coreMode", "");
            set(sender, "coreDescription", Component.empty());
            set(sender, "coreBlocked", false);
            sync(senderSync, receiverSync, false);
            CoreOrderConfirmationUi.update(screen);
            check("".equals(state.ae2ltpp$coreMode()) && !state.ae2ltpp$coreBlocked(), "return to ordinary crafting");
            finish("PASS: fresh transformed constructor and render; full/delta AE2 GUI sync; install/busy/ordinary transitions; null mode/description; button restoration");
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish("FAIL: " + failure);
        }
    }

    private static CraftConfirmMenu newMenu() {
        var mc = Minecraft.getInstance();
        var host = new PackagedPatternProviderBlockEntity(BlockPos.ZERO, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        host.setLevel(mc.level);
        return new CraftConfirmMenu(42, mc.player.getInventory(), host);
    }

    private static void set(CraftConfirmMenu menu, String name, Object value) throws ReflectiveOperationException {
        var field = CraftConfirmMenu.class.getDeclaredField("ae2ltpp$" + name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static void sync(DataSynchronization sender, DataSynchronization receiver, boolean full) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), Minecraft.getInstance().level.registryAccess());
        try {
            if (full) sender.writeFull(buffer); else sender.writeUpdate(buffer);
            receiver.readUpdate(buffer, new ShortOpenHashSet());
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(String result) {
        done = true;
        var mc = Minecraft.getInstance();
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("core-confirm-result.txt"), result + "\n");
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        mc.stop();
    }
}
