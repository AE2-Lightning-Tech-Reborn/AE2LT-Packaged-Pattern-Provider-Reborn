package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/** Real one-craft dispatch for Nature's Aura machines on Forge 1.20.1. */
public final class NaturesAuraAdapter implements MultiblockAdapter {
    public enum Kind {
        ALTAR("nature_altar", "altar", AdapterIds.NATURES_AURA_ALTAR),
        TREE("wood_stand", "tree_ritual", AdapterIds.NATURES_AURA_TREE),
        OFFERING("offering_table", "offering", AdapterIds.NATURES_AURA_OFFERING);

        final ResourceLocation block;
        final ResourceLocation type;
        final ResourceLocation core;

        Kind(String block, String type, ResourceLocation core) {
            this.block = id(block);
            this.type = id(type);
            this.core = core;
        }
    }

    private static final int MAX_UNITS = 32;
    private final Kind kind;

    public NaturesAuraAdapter(Kind kind) {
        this.kind = kind;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return kind.core;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return ModList.get().isLoaded("naturesaura")
                && be instanceof NaturesAuraManagedMachine
                && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(kind.block);
    }

    @Override
    public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return null;
        List<AEItemKey> units = patternUnits(pattern);
        if (units == null) return null;
        BlockPos center = kind == Kind.TREE ? treeCenter(level, pos) : pos;
        if (center == null) return null;
        List<Recipe<?>> selection = new ArrayList<>();
        for (Recipe<?> recipe : recipes(level)) {
            selection.add(recipe);
            if (matchesOutput(pattern, recipe, level) && assign(recipe, units) != null) {
                int powder = kind == Kind.TREE ? units.size() - treeIngredients(recipe).size() - 1 : 0;
                return new BindingResult(new Handle(recipe, center, powder, List.copyOf(selection)), BindingMode.REAL);
            }
        }
        return null;
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return handle instanceof Handle h && ready(level, pos, h);
    }

    @Override
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Handle h) || !ready(level, pos, h)) return null;
        Recipe<?> recipe = level.getRecipeManager().byKey(h.recipe.getId()).orElse(null);
        List<AEItemKey> units = actualUnits(inputs);
        if (recipe != h.recipe || recipe.getType() != recipeType() || units == null
                || !matchesOutput(pattern, recipe, level)) return null;
        List<AEItemKey> assignment = assign(recipe, units);
        if (assignment == null || !nativeSelects(level, pos, h, assignment)) return null;
        int materialCount = kind == Kind.TREE ? treeIngredients(recipe).size()
                : kind == Kind.OFFERING ? assignment.size() - 1 : 1;
        if (kind == Kind.TREE && (assignment.size() - materialCount - 1 != h.powder
                || treeStands(level, h.center).size() < materialCount)) return null;
        if (kind == Kind.TREE && !placeable(level, h.center, assignment.get(assignment.size() - 1))) return null;

        List<GenericStack> stacks = units.stream().map(key -> new GenericStack(key, 1)).toList();
        List<GenericStack> escrow = new ArrayList<>();
        boolean[] recoverable = {true};
        TargetSlot target = new TargetSlot(level, pos, null, stacks, InsertionStrategy.CUSTOM,
                (stack, action) -> {
                    if (action == Actionable.MODULATE) escrow.add(stack);
                    return stack.amount();
                });
        return new DispatchPlan(List.of(target), () -> {
            if (!ready(level, pos, h) || !nativeSelects(level, pos, h, assignment)
                    || (kind == Kind.TREE && !placeable(level, h.center, assignment.get(assignment.size() - 1)))) {
                throw new IllegalStateException("Nature's Aura target changed before commit");
            }
            List<Runnable> undo = new ArrayList<>();
            recoverable[0] = false;
            try {
                commit(level, pos, h.center, h.recipe, assignment, materialCount, undo);
            } catch (RuntimeException | LinkageError e) {
                for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
                recoverable[0] = true;
                throw e;
            }
            escrow.clear();
        }, (accepted, recovered) -> {
            if (recoverable[0]) {
                for (GenericStack stack : escrow) recovered.accept(stack);
                escrow.clear();
            }
        });
    }

    private boolean ready(ServerLevel level, BlockPos pos, Handle handle) {
        if (!level.isLoaded(pos) || !recognizesMain(level, pos, level.getBlockEntity(pos))) return false;
        if (!level.hasChunksAt(handle.center.offset(-4, -1, -4), handle.center.offset(4, 3, 4))) return false;
        if (kind == Kind.TREE) {
            if (!NaturesAuraAccess.positions("TREE_RITUAL", handle.center, 'W').contains(pos)) return false;
            for (BlockPos stand : treeStands(level, handle.center)) {
                BlockEntity be = level.getBlockEntity(stand);
                if (!empty(be) || NaturesAuraAccess.field(be, "recipe") != null) return false;
            }
            if (!level.getBlockState(handle.center).isAir()) return false;
            int missing = 0;
            for (BlockPos powder : NaturesAuraAccess.positions("TREE_RITUAL", handle.center, 'G')) {
                if (!level.isLoaded(powder)) return false;
                var state = level.getBlockState(powder);
                if (state.is(goldBlock())) continue;
                if (!state.isAir() || !goldBlock().defaultBlockState().canSurvive(level, powder)) return false;
                missing++;
            }
            return missing == handle.powder;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!empty(be) || !NaturesAuraAccess.complete(kind == Kind.ALTAR ? "ALTAR" : "OFFERING_TABLE", level, pos)) {
            return false;
        }
        if (kind == Kind.ALTAR) return NaturesAuraAccess.field(be, "currentRecipe") == null;
        return be instanceof NaturesAuraOfferingMachine
                && NaturesAuraAccess.field(be, "itemsToSpawn") instanceof Queue<?> queue && queue.isEmpty();
    }

    private static boolean empty(BlockEntity be) {
        if (be == null || NaturesAuraCraftState.active(be)) return false;
        var inventory = NaturesAuraAccess.items(be);
        return inventory != null && inventory.getStackInSlot(0).isEmpty();
    }

    private void commit(ServerLevel level, BlockPos owner, BlockPos center, Recipe<?> recipe,
            List<AEItemKey> assigned,
            int materialCount, List<Runnable> undo) {
        List<BlockPos> positions = kind == Kind.TREE ? treeStands(level, center) : List.of(owner);
        BlockEntity leader = level.getBlockEntity(positions.get(0));
        undo.add(() -> {
            leader.getPersistentData().remove(NaturesAuraCraftState.KEY);
            leader.setChanged();
        });
        NaturesAuraCraftState.begin(leader, owner);
        NaturesAuraCraftState.storeExpectedOutput(leader, recipe.getResultItem(level.registryAccess()));
        if (kind == Kind.TREE) {
            BlockEntity anchor = level.getBlockEntity(owner);
            undo.add(() -> {
                anchor.getPersistentData().remove(NaturesAuraCraftState.TREE_LINK);
                anchor.setChanged();
            });
            anchor.getPersistentData().putLong(NaturesAuraCraftState.TREE_LINK, center.asLong());
            anchor.setChanged();
        }
        if (kind == Kind.OFFERING) {
            put(level, owner, assigned.get(0).toStack(materialCount), undo);
            NaturesAuraCraftState.storeOfferingStart(leader, assigned.get(assigned.size() - 1).toStack(1));
            try {
                ((NaturesAuraOfferingMachine) leader).ae2ltpp$activateOffering();
            } catch (RuntimeException | LinkageError e) {
                CompatibilityDiagnostics.failure(NaturesAuraAdapter.class, "activateOffering", e);
                throw e;
            }
            if (!NaturesAuraCraftState.offeringStart(leader).isEmpty()) {
                throw new IllegalStateException("Nature's Aura offering did not consume its start item");
            }
        } else {
            for (int i = 0; i < materialCount; i++) {
                ItemStack input = assigned.get(i).toStack(1);
                put(level, positions.get(i), input, undo);
            }
            if (kind == Kind.TREE) {
                for (BlockPos powder : NaturesAuraAccess.positions("TREE_RITUAL", center, 'G')) {
                    if (level.getBlockState(powder).isAir()) {
                        place(level, powder, goldBlock().defaultBlockState(), undo);
                        NaturesAuraCraftState.recordPlacedPowder(leader, powder);
                    }
                }
                BlockItem sapling = (BlockItem) assigned.get(assigned.size() - 1).getItem();
                place(level, center, sapling.getBlock().defaultBlockState(), undo);
                NaturesAuraCraftState.recordPlacedSapling(leader, center);
                if (!(sapling.getBlock() instanceof BonemealableBlock growable)) {
                    throw new IllegalStateException("Nature's Aura sapling is not growable");
                }
                for (int attempts = 0; attempts < 4
                        && level.getBlockState(center).is(sapling.getBlock()); attempts++) {
                    growable.performBonemeal(level, level.random, center, level.getBlockState(center));
                }
                if (level.getBlockState(center).is(sapling.getBlock())) {
                    throw new IllegalStateException("Nature's Aura sapling did not grow");
                }
                NaturesAuraAccess.setRitual(leader, center, recipe);
                if (NaturesAuraAccess.recipe(leader) != recipe) {
                    throw new IllegalStateException("Nature's Aura ritual state was not installed");
                }
            }
        }
    }

    private static void put(ServerLevel level, BlockPos pos, ItemStack stack, List<Runnable> undo) {
        BlockEntity be = level.getBlockEntity(pos);
        var inventory = NaturesAuraAccess.items(be);
        if (inventory == null) throw new IllegalStateException("Nature's Aura inventory unavailable");
        ItemStack previous = inventory.getStackInSlot(0).copy();
        undo.add(() -> {
            inventory.setStackInSlot(0, previous);
            be.setChanged();
        });
        inventory.setStackInSlot(0, stack);
        be.setChanged();
    }

    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            List<Runnable> undo) {
        var previous = level.getBlockState(pos);
        undo.add(() -> level.setBlockAndUpdate(pos, previous));
        if (!level.setBlockAndUpdate(pos, state) || !level.getBlockState(pos).is(state.getBlock())) {
            throw new IllegalStateException("Nature's Aura ritual placement failed at " + pos);
        }
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter,
            IActionSource source) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return List.of();
        if (kind == Kind.TREE) {
            BlockPos center = treeCenter(level, pos);
            if (center == null) return List.of();
            for (BlockPos stand : treeStands(level, center)) {
                BlockEntity be = level.getBlockEntity(stand);
                if (be != null && NaturesAuraCraftState.owned(be, pos)) {
                    List<GenericStack> result = NaturesAuraCraftState.drain(be,
                            NaturesAuraAccess.field(be, "recipe") == null, filter);
                    if (!NaturesAuraCraftState.active(be)) {
                        BlockEntity anchor = level.getBlockEntity(pos);
                        if (anchor != null) {
                            anchor.getPersistentData().remove(NaturesAuraCraftState.TREE_LINK);
                            anchor.setChanged();
                        }
                    }
                    return result;
                }
            }
            return List.of();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!NaturesAuraCraftState.owned(be, pos)) return List.of();
        boolean finished = kind == Kind.ALTAR
                || NaturesAuraAccess.field(be, "itemsToSpawn") instanceof Queue<?> queue && queue.isEmpty();
        return NaturesAuraCraftState.drain(be, finished, filter);
    }

    private boolean nativeSelects(ServerLevel level, BlockPos pos, Handle handle, List<AEItemKey> assigned) {
        if (kind == Kind.ALTAR) return NaturesAuraAccess.altarSelects(level.getBlockEntity(pos), assigned.get(0).toStack(1), handle.recipe);
        for (Recipe<?> recipe : handle.selectionOrder) {
            if (level.getRecipeManager().byKey(recipe.getId()).orElse(null) != recipe) return false;
            if (kind == Kind.OFFERING) {
                Ingredient input = NaturesAuraAccess.ingredient(recipe, "input");
                if (input != null && input.test(assigned.get(0).toStack(1))) {
                    return recipe.getId().equals(handle.recipe.getId());
                }
            } else {
                Ingredient sapling = NaturesAuraAccess.ingredient(recipe, "saplingType");
                List<Ingredient> ingredients = treeIngredients(recipe);
                if (sapling != null && sapling.test(assigned.get(assigned.size() - 1).toStack(1))
                        && ingredients != null && ingredients.size() == assigned.size() - handle.powder - 1
                        && nativeTreeMatches(assigned.subList(0, ingredients.size()), ingredients)) {
                    return recipe.getId().equals(handle.recipe.getId());
                }
            }
        }
        return false;
    }

    static boolean nativeTreeMatches(List<AEItemKey> units, List<Ingredient> ingredients) {
        List<Ingredient> remaining = new ArrayList<>(ingredients);
        for (AEItemKey key : units) {
            boolean found = false;
            for (int i = remaining.size() - 1; i >= 0; i--) {
                if (remaining.get(i).test(key.toStack(1))) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    List<AEItemKey> assign(Recipe<?> recipe, List<AEItemKey> units) {
        List<Ingredient> requirements = new ArrayList<>();
        if (kind == Kind.TREE) {
            List<Ingredient> ingredients = treeIngredients(recipe);
            Ingredient sapling = NaturesAuraAccess.ingredient(recipe, "saplingType");
            if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 8 || sapling == null) return null;
            for (int i = ingredients.size() - 1; i >= 0; i--) requirements.add(ingredients.get(i));
            int powder = units.size() - ingredients.size() - 1;
            if (powder < 0 || powder > 16) return null;
            for (int i = 0; i < powder; i++) requirements.add(Ingredient.of(goldBlock()));
            requirements.add(sapling);
        } else {
            Ingredient input = NaturesAuraAccess.ingredient(recipe, "input");
            if (input == null) return null;
            int count = kind == Kind.ALTAR ? 1 : NaturesAuraAccess.amount(input);
            if (count < 1 || count > 16) return null;
            for (int i = 0; i < count; i++) requirements.add(input);
            if (kind == Kind.OFFERING) {
                Ingredient start = NaturesAuraAccess.ingredient(recipe, "startItem");
                if (start == null) return null;
                requirements.add(start);
            }
        }
        if (requirements.size() != units.size()) return null;
        List<AEItemKey> result = BoundedConstraintMatcher.matchExactly(units,
                requirements.stream().<Predicate<AEItemKey>>map(ingredient -> key -> ingredient.test(key.toStack(1)))
                        .toList());
        if (result == null) return null;
        if (kind == Kind.OFFERING) {
            for (int i = 1; i < result.size() - 1; i++) if (!result.get(0).equals(result.get(i))) return null;
        }
        if (kind == Kind.TREE && !(result.get(result.size() - 1).getItem() instanceof BlockItem item
                && item.getBlock() instanceof SaplingBlock)) return null;
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static List<Ingredient> treeIngredients(Recipe<?> recipe) {
        Object field = NaturesAuraAccess.field(recipe, "ingredients");
        if (field instanceof Ingredient[] ingredients) return List.of(ingredients);
        if (!(field instanceof List<?> list) || !list.stream().allMatch(Ingredient.class::isInstance)) return null;
        return (List<Ingredient>) list;
    }

    private static boolean matchesOutput(IPatternDetails pattern, Recipe<?> recipe, ServerLevel level) {
        if (pattern.getOutputs().length != 1) return false;
        var output = pattern.getOutputs()[0];
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty() || output.amount() != result.getCount() || !(output.what() instanceof AEItemKey key)) return false;
        AEItemKey actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? key.dropSecondary().equals(actual.dropSecondary()) : key.equals(actual);
    }

    private static List<AEItemKey> patternUnits(IPatternDetails pattern) {
        List<AEItemKey> units = new ArrayList<>();
        for (var input : pattern.getInputs()) {
            var possibilities = input.getPossibleInputs();
            if (possibilities.length == 0 || !(possibilities[0].what() instanceof AEItemKey key)) return null;
            long amount;
            try {
                amount = Math.multiplyExact(possibilities[0].amount(), input.getMultiplier());
            } catch (ArithmeticException e) {
                return null;
            }
            if (!append(units, key, amount)) return null;
        }
        return units;
    }

    private static List<AEItemKey> actualUnits(KeyCounter[] inputs) {
        List<AEItemKey> units = new ArrayList<>();
        for (KeyCounter input : inputs) for (var entry : input) {
            if (!(entry.getKey() instanceof AEItemKey key) || !append(units, key, entry.getLongValue())) return null;
        }
        return units;
    }

    private static boolean append(List<AEItemKey> units, AEItemKey key, long count) {
        if (count < 1 || count > MAX_UNITS - units.size()) return false;
        for (long i = 0; i < count; i++) units.add(key);
        return true;
    }

    private static BlockPos treeCenter(ServerLevel level, BlockPos anchor) {
        BlockEntity linked = level.getBlockEntity(anchor);
        if (linked != null && linked.getPersistentData().contains(NaturesAuraCraftState.TREE_LINK)) {
            return BlockPos.of(linked.getPersistentData().getLong(NaturesAuraCraftState.TREE_LINK));
        }
        BlockPos found = null;
        int best = 0;
        boolean ambiguous = false;
        for (BlockPos offset : NaturesAuraAccess.positions("TREE_RITUAL", BlockPos.ZERO, 'W')) {
            BlockPos center = anchor.subtract(offset);
            List<BlockPos> stands = treeStands(level, center);
            int score = stands.size() * 17;
            for (BlockPos powder : NaturesAuraAccess.positions("TREE_RITUAL", center, 'G')) {
                if (level.isLoaded(powder) && level.getBlockState(powder).is(goldBlock())) score++;
            }
            if (score > best) {
                found = center;
                best = score;
                ambiguous = false;
            } else if (score == best) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : found;
    }

    private static List<BlockPos> treeStands(ServerLevel level, BlockPos center) {
        return NaturesAuraAccess.positions("TREE_RITUAL", center, 'W').stream()
                .filter(pos -> level.isLoaded(pos)
                        && level.getBlockEntity(pos) instanceof NaturesAuraManagedMachine
                        && level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(id("wood_stand"))))
                .toList();
    }

    private static boolean placeable(ServerLevel level, BlockPos center, AEItemKey sapling) {
        return sapling.getItem() instanceof BlockItem item && item.getBlock() instanceof SaplingBlock
                && item.getBlock().defaultBlockState().canSurvive(level, center);
    }

    private RecipeType<?> recipeType() {
        return BuiltInRegistries.RECIPE_TYPE.get(kind.type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Recipe<?>> recipes(ServerLevel level) {
        return (List) level.getRecipeManager().getRecipesFor((RecipeType) recipeType(), null, level);
    }

    private static net.minecraft.world.level.block.Block goldBlock() {
        return BuiltInRegistries.BLOCK.get(id("gold_powder"));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("naturesaura", path);
    }

    private record Handle(Recipe<?> recipe, BlockPos center, int powder, List<Recipe<?>> selectionOrder) {
    }
}
