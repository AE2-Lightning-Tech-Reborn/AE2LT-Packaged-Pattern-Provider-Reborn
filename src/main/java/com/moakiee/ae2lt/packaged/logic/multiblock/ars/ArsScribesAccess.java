package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class ArsScribesAccess {
    private static final ResourceLocation TABLE = ResourceLocation.parse("ars_nouveau:scribes_table");
    private static final ResourceLocation GLYPH = ResourceLocation.parse("ars_nouveau:glyph");

    static Object call(Object value, String method) {
        try { return ReflectionSupport.findMethodCached(value.getClass(), method).orElseThrow().invoke(value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Ars scribe API " + method, e); }
    }

    static BlockEntity head(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ArsScribesMachine)) return null;
        var tile = level.getBlockEntity(pos);
        if (!BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()).equals(TABLE)) return null;
        var part = part(tile);
        if (!part.equals("head") && !part.equals("foot")) return null;
        var masterPos = part.equals("head") ? pos : pos.relative(tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (!level.isLoaded(masterPos)) return null;
        var found = call(tile, "getLogicTile");
        if (!(found instanceof BlockEntity head) || !(head instanceof ArsScribesMachine)
                || !Boolean.TRUE.equals(call(head, "isMasterTile"))) return null;
        var state = head.getBlockState();
        var footPos = head.getBlockPos().relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if (!level.isLoaded(footPos)) return null;
        var foot = level.getBlockEntity(footPos);
        if (!(foot instanceof ArsScribesMachine) || foot.getBlockState().getBlock() != state.getBlock()
                || !part(foot).equals("foot")
                || foot.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) != state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                || call(foot, "getLogicTile") != head) return null;
        return head;
    }

    private static String part(BlockEntity tile) {
        return tile.getBlockState().getValues().entrySet().stream().filter(e -> e.getKey().getName().equals("part"))
                .map(e -> e.getValue().toString()).findFirst().orElse("");
    }

    @SuppressWarnings("unchecked")
    static List<RecipeHolder<Recipe<?>>> recipes(ServerLevel level) {
        var type = BuiltInRegistries.RECIPE_TYPE.get(GLYPH);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }
    @SuppressWarnings("unchecked")
    static List<Ingredient> ingredients(Recipe<?> recipe) { return (List<Ingredient>) call(recipe, "getInputs"); }
    static long cost(Recipe<?> recipe) {
        int xp = ((Number) call(recipe, "getExp")).intValue();
        return xp < 0 ? -1 : (long) xp * com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy.MB_PER_XP;
    }
    private ArsScribesAccess() {}
}
