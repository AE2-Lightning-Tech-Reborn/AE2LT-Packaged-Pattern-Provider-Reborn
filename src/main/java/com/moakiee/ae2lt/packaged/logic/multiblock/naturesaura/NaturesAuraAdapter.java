package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** Physical, one-craft dispatch. Native machines own timing, Aura and recipe execution. */
public final class NaturesAuraAdapter implements MultiblockAdapter {
    public enum Kind {
        ALTAR("nature_altar", "altar", AdapterIds.NATURES_AURA_ALTAR),
        TREE("wood_stand", "tree_ritual", AdapterIds.NATURES_AURA_TREE),
        OFFERING("offering_table", "offering", AdapterIds.NATURES_AURA_OFFERING);

        final ResourceLocation block, type, core;
        Kind(String block, String type, ResourceLocation core) {
            this.block = id(block);
            this.type = id(type);
            this.core = core;
        }
    }

    private static final int MAX_UNITS = 32;
    private final Kind kind;

    public NaturesAuraAdapter(Kind kind) { this.kind = kind; }
    @Override public int priority() { return 100; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return kind.core; }

    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return ModList.get().isLoaded("naturesaura") && be instanceof NaturesAuraManagedMachine
                && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(kind.block);
    }

    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return null;
        var units = patternUnits(pattern);
        if (units == null) return null;
        BlockPos center = kind == Kind.TREE ? treeCenter(level, pos) : pos;
        if (center == null) return null;
        var selectionOrder = new ArrayList<RecipeHolder<?>>();
        for (var recipe : recipes(level)) {
            selectionOrder.add(recipe);
            if (matchesOutput(pattern, recipe, level) && assign(recipe, units) != null) {
                int powder = kind == Kind.TREE ? units.size() - treeIngredients(recipe).size() - 1 : 0;
                return new BindingResult(new Handle(recipe, center, powder, List.copyOf(selectionOrder)), BindingMode.REAL);
            }
        }
        return null;
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return handle instanceof Handle h && ready(level, pos, h);
    }

    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Handle h) || !ready(level, pos, h)) return null;
        var recipe = level.getRecipeManager().byKey(h.recipe().id()).orElse(null);
        var units = actualUnits(inputs);
        if (recipe != h.recipe() || recipe.value().getType() != recipeType() || units == null
                || !matchesOutput(pattern, recipe, level)) return null;
        var assignment = assign(recipe, units);
        if (assignment == null || !nativeSelects(level, pos, h, assignment)) return null;
        int materialCount = kind == Kind.TREE ? treeIngredients(recipe).size()
                : kind == Kind.OFFERING ? assignment.size() - 1 : 1;
        if (kind == Kind.TREE && (assignment.size() - materialCount - 1 != h.powder()
                || treeStands(level, h.center()).size() < materialCount)) return null;
        if (kind == Kind.TREE && !placeable(level, h.center(), assignment.getLast())) return null;

        // Stage paid inputs until all receipts are confirmed. No world mutation in SIMULATE.
        var escrow = new ArrayList<GenericStack>();
        boolean[] recoverable = {true};
        var stacks = units.stream().map(key -> new GenericStack(key, 1)).toList();
        var target = new TargetSlot(level, pos, null, stacks, InsertionStrategy.CUSTOM, (stack, action) -> {
            if (action == Actionable.MODULATE) escrow.add(stack);
            return stack.amount();
        });
        return new DispatchPlan(List.of(target), () -> {
            if (!ready(level, pos, h) || !nativeSelects(level, pos, h, assignment)
                    || (kind == Kind.TREE && !placeable(level, h.center(), assignment.getLast()))) {
                throw new IllegalStateException("Nature's Aura target changed before commit");
            }
            var undo = new ArrayList<Runnable>();
            recoverable[0] = false;
            try {
                commit(level, pos, h.center(), assignment, materialCount, undo);
            } catch (RuntimeException | LinkageError e) {
                for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
                recoverable[0] = true;
                throw e;
            }
            escrow.clear();
        }, (accepted, recovered) -> {
            if (recoverable[0]) {
                escrow.forEach(recovered);
                escrow.clear();
            }
        });
    }

    private boolean ready(ServerLevel level, BlockPos pos, Handle h) {
        if (!level.isLoaded(pos) || !recognizesMain(level, pos, level.getBlockEntity(pos))) return false;
        if (!level.hasChunksAt(h.center().offset(-4, -1, -4), h.center().offset(4, 3, 4))) return false;
        if (kind == Kind.TREE) {
            if (!NaturesAuraAccess.positions("TREE_RITUAL", h.center(), 'W').contains(pos)) return false;
            for (var stand : treeStands(level, h.center())) {
                var be = level.getBlockEntity(stand);
                if (!empty(be) || NaturesAuraAccess.field(be, "recipe") != null) return false;
            }
            if (!level.getBlockState(h.center()).isAir()) return false;
            int missing = 0;
            for (var powder : NaturesAuraAccess.positions("TREE_RITUAL", h.center(), 'G')) {
                if (!level.isLoaded(powder)) return false;
                var state = level.getBlockState(powder);
                if (state.is(goldBlock())) continue;
                if (!state.isAir() || !goldBlock().defaultBlockState().canSurvive(level, powder)) return false;
                missing++;
            }
            return missing == h.powder();
        }
        var be = level.getBlockEntity(pos);
        if (!empty(be) || !NaturesAuraAccess.complete(kind == Kind.ALTAR ? "ALTAR" : "OFFERING_TABLE", level, pos)) return false;
        if (kind == Kind.ALTAR) return NaturesAuraAccess.field(be, "currentRecipe") == null;
        return be instanceof NaturesAuraOfferingMachine
                && NaturesAuraAccess.field(be, "itemsToSpawn") instanceof Queue<?> queue && queue.isEmpty();
    }

    private static boolean empty(BlockEntity be) {
        if (be == null || NaturesAuraCraftState.active(be)) return false;
        var inventory = NaturesAuraAccess.items(be);
        return inventory != null && inventory.getStackInSlot(0).isEmpty();
    }

    private void commit(ServerLevel level, BlockPos owner, BlockPos center, List<AEItemKey> assigned, int materialCount,
            List<Runnable> undo) {
        var positions = kind == Kind.TREE ? treeStands(level, center) : List.of(owner);
        var leader = level.getBlockEntity(positions.getFirst());
        undo.add(() -> { leader.getPersistentData().remove(NaturesAuraCraftState.KEY); leader.setChanged(); });
        NaturesAuraCraftState.begin(leader, owner);
        if (kind == Kind.TREE) {
            var anchor = level.getBlockEntity(owner);
            undo.add(() -> { anchor.getPersistentData().remove(NaturesAuraCraftState.TREE_LINK); anchor.setChanged(); });
            anchor.getPersistentData().putLong(NaturesAuraCraftState.TREE_LINK, center.asLong());
            anchor.setChanged();
        }
        if (kind == Kind.OFFERING) {
            put(level, owner, assigned.getFirst().toStack(materialCount), undo);
            NaturesAuraCraftState.storeOfferingStart(leader, assigned.getLast().toStack(1));
            // All inputs now belong to the machine. Native execution can consume them, so a
            // failure here must never refund escrow on top of a partially completed offering.
            try {
                ((NaturesAuraOfferingMachine) leader).ae2ltpp$activateOffering();
            } catch (RuntimeException | LinkageError e) {
                CompatibilityDiagnostics.failure(NaturesAuraAdapter.class, "activateOffering", e);
            }
        } else {
            for (int i = 0; i < materialCount; i++) put(level, positions.get(i), assigned.get(i).toStack(1), undo);
            if (kind == Kind.TREE) {
                for (var pos : NaturesAuraAccess.positions("TREE_RITUAL", center, 'G')) {
                    if (level.getBlockState(pos).isAir()) place(level, pos, goldBlock().defaultBlockState(), undo);
                }
                var sapling = ((BlockItem) assigned.getLast().getItem()).getBlock().defaultBlockState();
                place(level, center, sapling, undo);
            }
        }
    }

    private static void put(ServerLevel level, BlockPos pos, ItemStack stack, List<Runnable> undo) {
        var be = level.getBlockEntity(pos);
        var inventory = NaturesAuraAccess.items(be);
        undo.add(() -> { inventory.setStackInSlot(0, ItemStack.EMPTY); be.setChanged(); });
        inventory.setStackInSlot(0, stack);
        be.setChanged();
    }

    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            List<Runnable> undo) {
        var previous = level.getBlockState(pos);
        undo.add(() -> level.setBlockAndUpdate(pos, previous));
        if (!level.setBlockAndUpdate(pos, state) || !level.getBlockState(pos).is(state.getBlock())) {
            throw new IllegalStateException("Ritual placement failed at " + pos);
        }
    }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return List.of();
        if (kind == Kind.TREE) {
            var center = treeCenter(level, pos);
            if (center == null) return List.of();
            for (var stand : treeStands(level, center)) {
                var be = level.getBlockEntity(stand);
                if (be != null && NaturesAuraCraftState.owned(be, pos)) {
                    var result = NaturesAuraCraftState.drain(be, NaturesAuraAccess.field(be, "recipe") == null);
                    if (!NaturesAuraCraftState.active(be)) {
                        var anchor = level.getBlockEntity(pos);
                        anchor.getPersistentData().remove(NaturesAuraCraftState.TREE_LINK);
                        anchor.setChanged();
                    }
                    return result;
                }
            }
            return List.of();
        }
        var be = level.getBlockEntity(pos);
        if (!NaturesAuraCraftState.owned(be, pos)) return List.of();
        boolean finished = kind == Kind.ALTAR || NaturesAuraAccess.field(be, "itemsToSpawn") instanceof Queue<?> q && q.isEmpty();
        return NaturesAuraCraftState.drain(be, finished);
    }

    private boolean nativeSelects(ServerLevel level, BlockPos pos, Handle handle, List<AEItemKey> assigned) {
        var expected = handle.recipe();
        if (kind == Kind.ALTAR) return NaturesAuraAccess.altarSelects(level.getBlockEntity(pos), assigned.getFirst().toStack(1), expected);
        // Recipe order is compiled at bind time; push never searches the whole recipe manager.
        for (var recipe : handle.selectionOrder()) {
            if (level.getRecipeManager().byKey(recipe.id()).orElse(null) != recipe) return false;
            if (kind == Kind.OFFERING) {
                var input = NaturesAuraAccess.ingredient(recipe.value(), "input");
                if (input != null && input.test(assigned.getFirst().toStack(1))) return recipe.id().equals(expected.id());
            } else {
                var sapling = NaturesAuraAccess.ingredient(recipe.value(), "saplingType");
                var ingredients = treeIngredients(recipe);
                if (sapling != null && sapling.test(assigned.getLast().toStack(1)) && ingredients != null
                        && nativeTreeMatches(assigned.subList(0, treeIngredients(expected).size()), ingredients)) {
                    return recipe.id().equals(expected.id());
                }
            }
        }
        return false;
    }

    static boolean nativeTreeMatches(List<AEItemKey> units, List<Ingredient> ingredients) {
        var remaining = new ArrayList<>(ingredients);
        for (var key : units) {
            boolean found = false;
            for (int i = remaining.size() - 1; i >= 0; i--) {
                if (remaining.get(i).test(key.toStack(1))) { remaining.remove(i); found = true; break; }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    private List<AEItemKey> assign(RecipeHolder<?> recipe, List<AEItemKey> units) {
        var requirements = new ArrayList<Ingredient>();
        if (kind == Kind.TREE) {
            var ingredients = treeIngredients(recipe);
            var sapling = NaturesAuraAccess.ingredient(recipe.value(), "saplingType");
            if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 8 || sapling == null) return null;
            // Reverse matches the native last-matching-ingredient selection order.
            for (int i = ingredients.size() - 1; i >= 0; i--) requirements.add(ingredients.get(i));
            int powder = units.size() - ingredients.size() - 1;
            if (powder < 0 || powder > 16) return null;
            for (int i = 0; i < powder; i++) requirements.add(Ingredient.of(goldBlock()));
            requirements.add(sapling);
        } else {
            var input = NaturesAuraAccess.ingredient(recipe.value(), "input");
            if (input == null) return null;
            int count = kind == Kind.ALTAR ? 1 : NaturesAuraAccess.amount(input);
            if (count < 1 || count > 16) return null;
            for (int i = 0; i < count; i++) requirements.add(input);
            if (kind == Kind.OFFERING) {
                var start = NaturesAuraAccess.ingredient(recipe.value(), "startItem");
                if (start == null) return null;
                requirements.add(start);
            }
        }
        if (requirements.size() != units.size()) return null;
        var result = new ArrayList<AEItemKey>();
        if (!assignAt(requirements, units, new boolean[units.size()], result, 0)) return null;
        if (kind == Kind.OFFERING) {
            for (int i = 1; i < result.size() - 1; i++) if (!result.getFirst().equals(result.get(i))) return null;
        }
        if (kind == Kind.TREE && !(result.getLast().getItem() instanceof BlockItem item && item.getBlock() instanceof SaplingBlock)) return null;
        return List.copyOf(result);
    }

    static boolean assignAt(List<Ingredient> required, List<AEItemKey> units, boolean[] used, List<AEItemKey> result, int index) {
        if (index == required.size()) return true;
        var tried = new java.util.HashSet<AEItemKey>();
        for (int i = 0; i < units.size(); i++) {
            var key = units.get(i);
            if (used[i] || !tried.add(key) || !required.get(index).test(key.toStack(1))) continue;
            used[i] = true;
            result.add(key);
            if (assignAt(required, units, used, result, index + 1)) return true;
            result.removeLast();
            used[i] = false;
        }
        return false;
    }

    @SuppressWarnings("unchecked") private static List<Ingredient> treeIngredients(RecipeHolder<?> recipe) {
        var field = NaturesAuraAccess.field(recipe.value(), "ingredients");
        if (!(field instanceof List<?> list) || !list.stream().allMatch(Ingredient.class::isInstance)) return null;
        return (List<Ingredient>) list;
    }

    private static boolean matchesOutput(IPatternDetails pattern, RecipeHolder<?> recipe, ServerLevel level) {
        if (pattern.getOutputs().size() != 1) return false;
        var output = pattern.getOutputs().getFirst();
        var result = recipe.value().getResultItem(level.registryAccess());
        if (result.isEmpty() || output.amount() != result.getCount() || !(output.what() instanceof AEItemKey key)) return false;
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0) ? key.dropSecondary().equals(actual.dropSecondary()) : key.equals(actual);
    }

    private static List<AEItemKey> patternUnits(IPatternDetails pattern) {
        var units = new ArrayList<AEItemKey>();
        for (var input : pattern.getInputs()) {
            var possibilities = input.getPossibleInputs();
            if (possibilities.length == 0 || !(possibilities[0].what() instanceof AEItemKey key)) return null;
            long amount;
            try { amount = Math.multiplyExact(possibilities[0].amount(), input.getMultiplier()); }
            catch (ArithmeticException e) { return null; }
            if (!append(units, key, amount)) return null;
        }
        return units;
    }

    private static List<AEItemKey> actualUnits(KeyCounter[] inputs) {
        var units = new ArrayList<AEItemKey>();
        for (var input : inputs) for (var entry : input) {
            if (!(entry.getKey() instanceof AEItemKey key) || !append(units, key, entry.getLongValue())) return null;
        }
        return units;
    }

    private static boolean append(List<AEItemKey> units, AEItemKey key, long count) {
        if (count < 1 || count > MAX_UNITS - units.size()) return false;
        for (int i = 0; i < count; i++) units.add(key);
        return true;
    }

    private static BlockPos treeCenter(ServerLevel level, BlockPos anchor) {
        var linked = level.getBlockEntity(anchor);
        if (linked != null && linked.getPersistentData().contains(NaturesAuraCraftState.TREE_LINK)) {
            return BlockPos.of(linked.getPersistentData().getLong(NaturesAuraCraftState.TREE_LINK));
        }
        BlockPos found = null;
        int best = 0;
        boolean ambiguous = false;
        for (var offset : NaturesAuraAccess.positions("TREE_RITUAL", BlockPos.ZERO, 'W')) {
            var center = anchor.subtract(offset);
            var stands = treeStands(level, center);
            int score = stands.size() * 17;
            for (var powder : NaturesAuraAccess.positions("TREE_RITUAL", center, 'G')) {
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
                .filter(pos -> level.isLoaded(pos) && level.getBlockEntity(pos) instanceof NaturesAuraManagedMachine
                        && level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(id("wood_stand"))))
                .toList();
    }

    private static boolean placeable(ServerLevel level, BlockPos center, AEItemKey sapling) {
        return sapling.getItem() instanceof BlockItem item && item.getBlock() instanceof SaplingBlock
                && item.getBlock().defaultBlockState().canSurvive(level, center);
    }

    private RecipeType<?> recipeType() { return BuiltInRegistries.RECIPE_TYPE.get(kind.type); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private List<RecipeHolder<?>> recipes(ServerLevel level) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) recipeType());
    }
    private static net.minecraft.world.level.block.Block goldBlock() { return BuiltInRegistries.BLOCK.get(id("gold_powder")); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("naturesaura", path); }
    private record Handle(RecipeHolder<?> recipe, BlockPos center, int powder, List<RecipeHolder<?>> selectionOrder) {}
}
