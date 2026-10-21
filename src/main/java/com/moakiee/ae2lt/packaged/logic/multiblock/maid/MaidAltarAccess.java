package com.moakiee.ae2lt.packaged.logic.multiblock.maid;

import java.lang.reflect.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.world.Container;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.ItemStackHandler;

/** Forge TLM 1.5.x boundary using its player capability and native altar inventory. */
public final class MaidAltarAccess {
    private static final String BASE = "com.github.tartaricacid.touhoulittlemaid.";
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5a14265e-4415-42c8-af59-197b8f795081"), "[LTPP Altar]");
    private static Access cached;
    private static boolean attempted;
    private record Access(Class<?> altar, Method blocks, Method pillars, Method data, Method place,
                          Method refresh, Field inventory, Method power, Method itemCraft, Method craft,
                          Method centre, Capability<Object> capability, Constructor<?> inventoryConstructor,
                          Method setPower, Method remaining) {}
    public record Layout(BlockEntity anchor, List<BlockEntity> pillars, BlockPos output) {}

    @SuppressWarnings("unchecked")
    private static Access access() {
        if (!attempted) {
            attempted = true;
            try {
                var altar = Class.forName(BASE + "tileentity.TileEntityAltar");
                var positions = Class.forName(BASE + "util.PosListData");
                var recipe = Class.forName(BASE + "crafting.AltarRecipe");
                var block = Class.forName(BASE + "block.BlockAltar");
                var power = Class.forName(BASE + "capability.PowerCapability");
                var craft = block.getDeclaredMethod("altarCraft", Level.class, altar, Player.class);
                var centre = block.getDeclaredMethod("getCentrePos", positions, BlockPos.class);
                craft.setAccessible(true);
                centre.setAccessible(true);
                var capability = (Capability<Object>) Class.forName(BASE + "capability.PowerCapabilityProvider")
                        .getField("POWER_CAP").get(null);
                cached = new Access(altar, altar.getMethod("getBlockPosList"), altar.getMethod("getCanPlaceItemPosList"),
                        positions.getMethod("getData"), altar.getMethod("isCanPlaceItem"), altar.getMethod("refresh"),
                        altar.getField("handler"), recipe.getMethod("getPowerCost"), recipe.getMethod("isItemCraft"),
                        craft, centre, capability, Class.forName(BASE + "inventory.AltarRecipeInventory").getConstructor(),
                        power.getMethod("set", float.class), power.getMethod("get"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure("api", e); }
        }
        return cached;
    }
    public static boolean isAltar(BlockEntity be) {
        return be != null && be.getClass().getName().equals(BASE + "tileentity.TileEntityAltar");
    }
    public static boolean live(BlockEntity be) {
        var level = be.getLevel();
        return !be.isRemoved() && level != null && level.isLoaded(be.getBlockPos())
                && level.getBlockEntity(be.getBlockPos()) == be;
    }
    @SuppressWarnings("unchecked")
    public static Layout layout(ServerLevel level, BlockPos pos, boolean empty) {
        var a = access();
        var clicked = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
        if (a == null || !isAltar(clicked)) return null;
        try {
            var blockList = (List<BlockPos>) a.data().invoke(a.blocks().invoke(clicked));
            var pillars = (List<BlockPos>) a.data().invoke(a.pillars().invoke(clicked));
            if (blockList.isEmpty() || blockList.size() > 256 || !blockList.contains(pos)
                    || pillars.size() != 6 || new HashSet<>(pillars).size() != 6) return null;
            for (var part : blockList) {
                if (part.distSqr(pos) > 256 || !level.isLoaded(part)) return null;
                var be = level.getBlockEntity(part);
                if (!isAltar(be) || !blockList.equals(a.data().invoke(a.blocks().invoke(be)))
                        || !pillars.equals(a.data().invoke(a.pillars().invoke(be)))) return null;
            }
            var found = new ArrayList<BlockEntity>();
            for (var part : pillars) {
                var be = level.getBlockEntity(part);
                if (!blockList.contains(part) || !isAltar(be) || !(boolean) a.place().invoke(be)
                        || (empty && !inventory(be).getStackInSlot(0).isEmpty())) return null;
                found.add(be);
            }
            var anchor = found.get(0);
            var centre = (BlockPos) a.centre().invoke(anchor.getBlockState().getBlock(),
                    a.blocks().invoke(anchor), anchor.getBlockPos());
            return new Layout(anchor, List.copyOf(found), centre.above(2));
        } catch (ReflectiveOperationException | RuntimeException e) { failure("layout", e); return null; }
    }
    public static ItemStackHandler inventory(BlockEntity be) {
        try { return (ItemStackHandler) access().inventory().get(be); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public static void refresh(BlockEntity be) {
        try { access().refresh().invoke(be); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public static float power(Recipe<?> recipe) {
        try { return (float) access().power().invoke(recipe); }
        catch (ReflectiveOperationException | RuntimeException e) { return Float.NaN; }
    }
    public static boolean itemCraft(Recipe<?> recipe) {
        try { return access() != null && (boolean) access().itemCraft().invoke(recipe); }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    @SuppressWarnings("unchecked")
    public static RecipeType<Recipe<Container>> recipeType() {
        if (access() == null) return null;
        try {
            return (RecipeType<Recipe<Container>>) Class.forName(BASE + "init.InitRecipes").getField("ALTAR_CRAFTING").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) { failure("recipeType", e); return null; }
    }
    public static FakePlayer actor(ServerLevel level) { return FakePlayerFactory.get(level, PROFILE); }
    private static Object powerCapability(FakePlayer player) {
        return player.getCapability(access().capability()).orElseThrow(() -> new IllegalStateException("Missing native P capability"));
    }
    public static void setPower(FakePlayer player, float power) {
        try { access().setPower().invoke(powerCapability(player), power); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public static float remainingPower(FakePlayer player) {
        try { return (float) access().remaining().invoke(powerCapability(player)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static Container input(List<ItemStack> stacks) {
        try {
            var inventory = (Container) access().inventoryConstructor().newInstance();
            for (int i = 0; i < stacks.size(); i++) inventory.setItem(i, stacks.get(i));
            return inventory;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public static void craft(ServerLevel level, Layout layout, FakePlayer actor) {
        try { access().craft().invoke(layout.anchor().getBlockState().getBlock(), level, layout.anchor(), actor); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Native maid altar craft failed", e); }
    }
    static Container input(Layout layout) {
        return input(layout.pillars().stream().map(be -> inventory(be).getStackInSlot(0)).toList());
    }
    static void failure(String phase, Throwable e) { CompatibilityDiagnostics.failure(MaidAltarAccess.class, phase, e); }
    private MaidAltarAccess() {}
}
