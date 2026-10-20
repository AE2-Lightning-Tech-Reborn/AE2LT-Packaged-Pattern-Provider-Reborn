package com.moakiee.ae2lt.packaged.logic.multiblock.apothic;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.items.IItemHandler;

/** Optional API boundary. Call specialized methods so recipe subclasses retain their semantics. */
public final class ApothicAccess {
    public static final ResourceLocation RAVEN = ResourceLocation.parse("apothic_enchanting:raven_enchanting_table");
    private static final String BASE = "dev.shadowsoffire.apothic_enchanting.";
    private ApothicAccess() {}
    private static final class Api {
        static final Class<?> RECIPE = type(BASE + "table.infusion.InfusionRecipe");
        static final Method MATCH = method(RECIPE, "matches", ItemStack.class, float.class, float.class, float.class);
        static final Method ASSEMBLE = method(RECIPE, "assemble", ItemStack.class, float.class, float.class, float.class);
        static final Method REQUIREMENTS = method(RECIPE, "getRequirements");
        static final Method INPUT = method(RECIPE, "getInput");
        static final Class<?> STATS = type(BASE + "table.EnchantingStatRegistry$Stats");
        static final Method E = method(STATS, "eterna"), Q = method(STATS, "quanta"), A = method(STATS, "arcana");
        static final Method XP = method(type(BASE + "util.MiscUtil"), "getExpCostForSlot", int.class, int.class);
        static final AttachmentType<?> INVENTORY = attachment();
        private static AttachmentType<?> attachment() {
            try { return (AttachmentType<?>) type(BASE + "table.EnchantmentTableItemHandler").getField("TYPE").get(null); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
    private static Class<?> type(String name) {
        try { return Class.forName(name); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static Method method(Class<?> type, String name, Class<?>... args) {
        try { return type.getMethod(name, args); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static Object invoke(Method method, Object instance, Object... args) {
        try { return method.invoke(instance, args); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<RecipeHolder<?>> recipes(Level level) {
        var type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse("apothic_enchanting:infusion"));
        return type == null ? List.of() : (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }
    public static Ingredient input(Object recipe) { return (Ingredient) invoke(Api.INPUT, recipe); }
    public static float[] stats(Object recipe) {
        var stats = invoke(Api.REQUIREMENTS, recipe);
        return new float[]{(float) invoke(Api.E, stats), (float) invoke(Api.Q, stats), (float) invoke(Api.A, stats)};
    }
    public static ItemStack assemble(Object recipe, ItemStack input) {
        var s = stats(recipe);
        if (!(boolean) invoke(Api.MATCH, recipe, input, s[0], s[1], s[2])) return ItemStack.EMPTY;
        return (ItemStack) invoke(Api.ASSEMBLE, recipe, input.copy(), s[0], s[1], s[2]);
    }
    public static int experience(Object recipe) {
        float eterna = stats(recipe)[0];
        if (!Float.isFinite(eterna) || eterna > 10000) return -1;
        return (int) invoke(Api.XP, null, Math.max(3, (int) Math.ceil(eterna)), 2);
    }
    public static IItemHandler inventory(BlockEntity table) { return (IItemHandler) table.getData(Api.INVENTORY); }
}
