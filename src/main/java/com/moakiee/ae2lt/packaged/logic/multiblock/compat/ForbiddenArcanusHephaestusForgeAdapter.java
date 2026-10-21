package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAForgeConversion;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraftforge.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.item.HephaestusUpgradeReceiptItem;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPItems;

public final class ForbiddenArcanusHephaestusForgeAdapter implements MultiblockAdapter {
    private static final ResourceLocation BLOCK = new ResourceLocation("forbidden_arcanus", "hephaestus_forge");
    private static final ResourceKey<Registry<Object>> RITUALS = ResourceKey.createRegistryKey(
            new ResourceLocation("forbidden_arcanus", "hephaestus_forge/ritual"));
    private static final String TILE = "com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity";
    private static final String PEDESTAL = "com.stal111.forbidden_arcanus.common.block.entity.PedestalBlockEntity";
    private static final String RESULT = "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult";
    private static final String UPGRADE = "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult";
    private static final String CLAIM = "forbidden_arcanus_hephaestus_forge:harvest";
    private static final String PROOF = CLAIM + ":nonce";
    private static final String PENDING_NONCE = "ae2ltpp_hephaestus_pending_nonce";
    private static final String PENDING_RITUAL = "ae2ltpp_hephaestus_pending_ritual";
    private static final String PENDING_TIER = "ae2ltpp_hephaestus_pending_tier";
    private static final String COMPLETED_NONCE = "ae2ltpp_hephaestus_completed_nonce";
    private static final String OWNER_DIMENSION = "ae2ltpp_hephaestus_owner_dimension";
    private static final String OWNER_POSITION = "ae2ltpp_hephaestus_owner_position";
    private static final int[][] OFFSETS = {
            {-3, 0}, {-2, -2}, {0, -3}, {2, -2}, {3, 0}, {2, 2}, {0, 3}, {-2, 2}
    };

    @Override public int priority() { return 100; }

    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity entity) {
        return entity != null && ModList.get().isLoaded("forbidden_arcanus")
                && TILE.equals(entity.getClass().getName()) && BLOCK.equals(AdapterBlocks.idOf(entity.getBlockState()));
    }

    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.FORBIDDEN_ARCANUS_HEPHAESTUS_FORGE;
    }

    @Override @Nullable public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return null;
        var conversion = FAForgeConversion.match(level, pattern);
        if (conversion != null) return new BindingResult(conversion, BindingMode.REAL);
        if (pattern.getOutputs().length != 1
                || !(pattern.getOutputs()[0].what() instanceof AEItemKey)) return null;
        var matches = new ArrayList<Ritual>();
        for (var entry : level.registryAccess().registryOrThrow(RITUALS).entrySet()) {
            var ritual = entry.getValue();
            var result = call(ritual, "result");
            if (result == null) continue;
            ItemStack stack;
            int requiredTier = 0;
            if (RESULT.equals(result.getClass().getName())) {
                if (!(call(result, "getResult") instanceof ItemStack item) || item.isEmpty()) continue;
                stack = item;
            } else if (UPGRADE.equals(result.getClass().getName())) {
                var required = call(result, "getRequiredTier");
                var upgraded = call(result, "getUpgradedTier");
                if (!(required instanceof Integer oldTier) || !(upgraded instanceof Integer newTier)
                        || oldTier < 1 || newTier != oldTier + 1 || newTier > 5) continue;
                requiredTier = oldTier;
                stack = HephaestusUpgradeReceiptItem.forTier(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get(), newTier);
            } else continue;
            if (pattern.getOutputs()[0].amount() != stack.getCount()
                    || !pattern.getOutputs()[0].what().equals(AEItemKey.of(stack))) continue;
            matches.add(new Ritual(entry.getKey().location(), ritual, stack.copy(), requiredTier));
        }
        return matches.isEmpty() ? null : new BindingResult(List.copyOf(matches), BindingMode.REAL);
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return canDispatch(level, pos, handle, AdapterPersistentScope.NOOP);
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle, AdapterPersistentScope scope) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return false;
        if (handle instanceof FAForgeConversion.Match) return FAForgeConversion.idle(level.getBlockEntity(pos))
                && SlotHarvestOwnership.load(level, pos, scope, CLAIM) == null;
        if (!(handle instanceof List<?>) || level.getBlockEntity(pos).getPersistentData().contains(FAForgeConversion.JOB)) return false;
        var entity = level.getBlockEntity(pos);
        var manager = call(entity, "getRitualManager");
        if (manager == null || !Boolean.FALSE.equals(call(manager, "isRitualActive"))) return false;
        if (!formed(level, pos)) return false;
        if (!stack(entity).isEmpty() || !pedestalsEmpty(level, pos)) return false;
        var claim = SlotHarvestOwnership.load(level, pos, scope, CLAIM);
        if (claim != null) {
            if (!claim.expectedOutput().is(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get())) {
                SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
            } else {
                if (completionProved(level, pos, scope)) return false;
                clearProof(level, pos, scope);
                SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
            }
        }
        if (!entity.getPersistentData().getString(COMPLETED_NONCE).isEmpty()) {
            if (!abandonedProof(level, pos)) return false;
        }
        return !SlotHarvestOwnership.blocksDispatch(level, pos, scope, CLAIM, true);
    }

    @Override @Nullable public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        return planWithBinding(level, pos, pattern, inputs, handle, source, AdapterPersistentScope.NOOP);
    }

    @Override @Nullable public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source, AdapterPersistentScope scope) {
        if (handle instanceof FAForgeConversion.Match) {
            if (!canDispatch(level, pos, handle, scope)) return null;
            var match = FAForgeConversion.match(level, pattern, inputs);
            return match == null ? null : FAForgeConversion.plan(level, level.getBlockEntity(pos), match, scope);
        }
        if (!(handle instanceof List<?> rituals) || !canDispatch(level, pos, handle, scope)) return null;
        var supplied = supplied(inputs);
        if (supplied == null) return null;
        int currentTier = forgeTier(level, pos);
        for (var candidate : rituals) {
            if (!(candidate instanceof Ritual ritual)) continue;
            if (ritual.requiredTier() != 0 && currentTier != ritual.requiredTier()) continue;
            if (!resourcesReady(level.getBlockEntity(pos), ritual.value())) continue;
            var main = call(ritual.value(), "mainIngredient");
            var required = call(ritual.value(), "inputs");
            if (!(main instanceof Ingredient ingredient) || !(required instanceof List<?> pedestalInputs)) continue;
            var slots = expand(pedestalInputs);
            if (slots == null || slots.size() + 1 != supplied.size()) continue;
            for (int index = 0; index < supplied.size(); index++) {
                var mainStack = supplied.get(index);
                if (!ingredient.test(mainStack) || ItemStack.isSameItemSameTags(mainStack, ritual.output())) continue;
                var remaining = new ArrayList<>(supplied);
                remaining.remove(index);
                var matched = match(slots, remaining);
                if (matched == null) continue;
                var targets = new ArrayList<TargetSlot>();
                for (int slot = 0; slot < matched.size(); slot++) {
                    var pedestalPos = pedestalPos(pos, slot);
                    targets.add(target(level, pedestalPos, matched.get(slot), false));
                }
                targets.add(target(level, pos, mainStack, true));
                return new DispatchPlan(targets, () -> start(level, pos, scope, ritual),
                        (accepted, recovered) -> {
                            var manager = call(level.getBlockEntity(pos), "getRitualManager");
                            if (!Boolean.FALSE.equals(call(manager, "isRitualActive"))) return;
                            boolean fullyRecovered = true;
                            for (var insertion : accepted) {
                                var target = insertion.target();
                                var entity = level.getBlockEntity(target.pos());
                                if (entity == null) {
                                    fullyRecovered = false;
                                    continue;
                                }
                                var current = target.pos().equals(pos) ? stack(entity) : pedestalStack(entity);
                                var expected = ((AEItemKey) insertion.stack().what()).toStack(1);
                                if (current.getCount() != 1 || !ItemStack.isSameItemSameTags(current, expected)) {
                                    fullyRecovered = false;
                                    continue;
                                }
                                if (target.pos().equals(pos)) mutate(entity, "setStack", new Class<?>[]{int.class, ItemStack.class}, 4, ItemStack.EMPTY);
                                else {
                                    mutate(entity, "setStackAndSync", new Class<?>[]{ItemStack.class, boolean.class}, ItemStack.EMPTY, false);
                                    var definition = call(call(level.getBlockEntity(pos), "getEssenceManager"), "getCurrentEssences");
                                    if (definition == null) throw new IllegalStateException("Cannot recover ritual cache");
                                    mutate(manager, "updateIngredient", new Class<?>[]{entity.getClass(), ItemStack.class, definition.getClass()},
                                            entity, ItemStack.EMPTY, definition);
                                }
                                recovered.accept(insertion.stack());
                            }
                            if (fullyRecovered) {
                                SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
                                clearProof(level, pos, scope);
                            }
                        });
            }
        }
        return null;
    }

    @Override public boolean supportsPatternIndependentHarvest() { return true; }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source) { return List.of(); }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source, AdapterPersistentScope scope) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return List.of();
        if (FAForgeConversion.hasClaim(level, pos, scope))
            return FAForgeConversion.collect(level, pos, scope, filter);
        var state = SlotHarvestOwnership.load(level, pos, scope, CLAIM);
        if (state == null) return List.of();
        var entity = level.getBlockEntity(pos);
        var manager = call(entity, "getRitualManager");
        if (manager == null || !Boolean.FALSE.equals(call(manager, "isRitualActive"))) return List.of();
        var expected = state.expectedOutput();
        int upgradedTier = receiptTier(expected);
        if (upgradedTier != 0) {
            if (!completionProved(level, pos, scope) || forgeTier(level, pos) != upgradedTier || !stack(entity).isEmpty()
                    || !filter.matches(AEItemKey.of(expected)) || !upgradeClaimMatches(level, state, upgradedTier)) return List.of();
            SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
            clearProof(level, pos, scope);
            return List.of(new GenericStack(AEItemKey.of(expected), 1));
        }
        var output = stack(entity);
        if (output.getCount() != expected.getCount() || !ItemStack.isSameItemSameTags(output, expected)
                || !filter.matches(AEItemKey.of(output))) return List.of();
        mutate(entity, "setStack", new Class<?>[]{int.class, ItemStack.class}, 4, ItemStack.EMPTY);
        SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
        return List.of(new GenericStack(AEItemKey.of(output), output.getCount()));
    }

    private static void start(ServerLevel level, BlockPos pos, AdapterPersistentScope scope, Ritual ritual) {
        if (ritual.requiredTier() != 0 && forgeTier(level, pos) != ritual.requiredTier())
            throw new IllegalStateException("Forge tier changed during dispatch");
        var entity = level.getBlockEntity(pos);
        var manager = call(entity, "getRitualManager");
        var essences = call(entity, "getEssenceManager");
        var definition = call(essences, "getCurrentEssences");
        if (manager == null || essences == null || definition == null) throw new IllegalStateException("Forge state unavailable");
        for (int slot = 0; slot < OFFSETS.length; slot++) {
            var pedestal = level.getBlockEntity(pedestalPos(pos, slot));
            if (pedestal == null || !PEDESTAL.equals(pedestal.getClass().getName()))
                throw new IllegalStateException("Forge pedestal disappeared");
            if (!pedestalStack(pedestal).isEmpty()) mutate(manager, "updateIngredient",
                    new Class<?>[]{pedestal.getClass(), ItemStack.class, definition.getClass()},
                    pedestal, pedestalStack(pedestal), definition);
        }
        mutate(manager, "updateValidRitual", new Class<?>[]{definition.getClass()}, definition);
        var valid = ReflectionSupport.findDeclaredFieldCached(manager.getClass(), "validRitual");
        Object selected;
        try { selected = valid.orElseThrow().get(manager); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot check selected ritual", e); }
        if (selected != ritual.value()) throw new IllegalStateException("Selected ritual changed during dispatch");
        var storage = call(essences, "getStorage");
        if (storage == null) throw new IllegalStateException("Forge essence storage unavailable");
        SlotHarvestOwnership.store(level, pos, scope, CLAIM, ritual.output(), ritual.id());
        if (ritual.requiredTier() != 0) {
            var nonce = UUID.randomUUID().toString();
            scope.setState(pos, proofKey(level), nonce);
            var data = entity.getPersistentData();
            data.putString(PENDING_NONCE, nonce);
            data.putString(PENDING_RITUAL, ritual.id().toString());
            data.putInt(PENDING_TIER, ritual.requiredTier() + 1);
            if (scope instanceof PackagedPatternProviderBlockEntity provider && provider.getLevel() instanceof ServerLevel providerLevel) {
                data.putString(OWNER_DIMENSION, providerLevel.dimension().location().toString());
                data.putLong(OWNER_POSITION, provider.getBlockPos().asLong());
            }
            entity.setChanged();
        }
        boolean[] started = {false};
        try {
            mutate(manager, "tryStartRitual", new Class<?>[]{storage.getClass(), BooleanConsumer.class},
                    storage, (BooleanConsumer) value -> started[0] = value);
        } catch (RuntimeException failure) {
            if (Boolean.FALSE.equals(call(manager, "isRitualActive"))) {
                SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
                clearProof(level, pos, scope);
            }
            throw failure;
        }
        if (!started[0]) {
            if (Boolean.FALSE.equals(call(manager, "isRitualActive"))) {
                SlotHarvestOwnership.clear(level, pos, scope, CLAIM);
                clearProof(level, pos, scope);
            }
            throw new IllegalStateException("Forge refused to start ritual");
        }
        entity.setChanged();
    }

    private static TargetSlot target(ServerLevel level, BlockPos pos, ItemStack expected, boolean main) {
        var key = AEItemKey.of(expected);
        return new TargetSlot(level, pos, null, List.of(new GenericStack(key, 1)), InsertionStrategy.CUSTOM,
                (stack, mode) -> {
                    if (stack.amount() != 1 || !key.equals(stack.what())) return 0L;
                    var entity = level.getBlockEntity(pos);
                    if (entity == null || !(main ? TILE : PEDESTAL).equals(entity.getClass().getName())
                            || !(main ? stack(entity) : pedestalStack(entity)).isEmpty()) return 0L;
                    if (mode != Actionable.SIMULATE) {
                        if (main) mutate(entity, "setStack", new Class<?>[]{int.class, ItemStack.class}, 4, expected.copy());
                        else mutate(entity, "setStackAndSync", new Class<?>[]{ItemStack.class, boolean.class}, expected.copy(), false);
                    }
                    return 1L;
                });
    }

    @Nullable private static List<ItemStack> supplied(KeyCounter[] counters) {
        var items = new ArrayList<ItemStack>();
        for (var counter : counters) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() > 9 || entry.getLongValue() < 1) return null;
            for (long count = 0; count < entry.getLongValue(); count++) items.add(key.toStack(1));
            if (items.size() > 9) return null;
        }
        return items.isEmpty() ? null : items;
    }

    @Nullable private static List<Ingredient> expand(List<?> inputs) {
        var result = new ArrayList<Ingredient>();
        for (var input : inputs) {
            var ingredient = call(input, "ingredient");
            var count = call(input, "amount");
            if (!(ingredient instanceof Ingredient value) || !(count instanceof Integer amount)
                    || amount < 1 || amount > 8 || result.size() + amount > 8) return null;
            for (int index = 0; index < amount; index++) result.add(value);
        }
        return result;
    }

    @Nullable static List<ItemStack> match(List<Ingredient> ingredients, List<ItemStack> remaining) {
        if (ingredients.isEmpty()) return remaining.isEmpty() ? List.of() : null;
        for (int index = 0; index < remaining.size(); index++) {
            if (!ingredients.get(0).test(remaining.get(index))) continue;
            var rest = new ArrayList<>(remaining);
            var first = rest.remove(index);
            var suffix = match(ingredients.subList(1, ingredients.size()), rest);
            if (suffix != null) {
                var result = new ArrayList<ItemStack>();
                result.add(first);
                result.addAll(suffix);
                return result;
            }
        }
        return null;
    }

    private static boolean pedestalsEmpty(ServerLevel level, BlockPos pos) {
        for (int slot = 0; slot < OFFSETS.length; slot++) {
            var entity = level.getBlockEntity(pedestalPos(pos, slot));
            if (entity == null || !PEDESTAL.equals(entity.getClass().getName()) || !pedestalStack(entity).isEmpty()) return false;
        }
        return true;
    }

    private static boolean formed(ServerLevel level, BlockPos pos) {
        for (var check : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, 0, 8))) {
            if (!level.hasChunkAt(check)) return false;
        }
        var type = ReflectionSupport.findClassCached("com.stal111.forbidden_arcanus.common.block.ModBlockPatterns");
        if (type.isEmpty()) return false;
        var field = ReflectionSupport.findFieldCached(type.get(), "BASE_HEPHAESTUS_PATTERN");
        try {
            if (field.isEmpty() || !(field.get().get(null) instanceof BlockPattern pattern)) return false;
            var match = pattern.find(level, pos.below());
            return match != null && pos.below().equals(match.getBlock(4, 0, 4).getPos());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean resourcesReady(Object entity, Object ritual) {
        var available = call(call(entity, "getEssenceManager"), "getCurrentEssences");
        var required = modifiedEssences(entity, ritual);
        if (available == null || required == null) return false;
        var enough = ReflectionSupport.findMethodCached(available.getClass(), "hasMoreThan", required.getClass())
                .flatMap(method -> ReflectionSupport.invoke(method, available, required)).orElse(null);
        if (!Boolean.TRUE.equals(enough)) return false;
        var requirementsMethod = ReflectionSupport.findMethodCached(ritual.getClass(), "requirements");
        if (requirementsMethod.isEmpty()) return false;
        try {
            var requirements = requirementsMethod.get().invoke(ritual);
            if (requirements == null) return true;
            var manager = call(entity, "getRitualManager");
            if (manager == null) return false;
            var tierField = ReflectionSupport.findDeclaredFieldCached(manager.getClass(), "forgeTier");
            var accessorField = ReflectionSupport.findDeclaredFieldCached(manager.getClass(), "enhancerAccessor");
            if (tierField.isEmpty() || accessorField.isEmpty()) return false;
            var enhancers = call(accessorField.get().get(manager), "getEnhancers");
            if (!(enhancers instanceof List<?>)) return false;
            var tier = tierField.get().getInt(manager);
            return ReflectionSupport.findMethodCached(requirements.getClass(), "checkRequirements", int.class, List.class)
                    .flatMap(method -> ReflectionSupport.invoke(method, requirements, tier, enhancers))
                    .filter(Boolean.TRUE::equals).isPresent();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Nullable private static Object modifiedEssences(Object entity, Object ritual) {
        var raw = call(ritual, "essences");
        var manager = call(entity, "getRitualManager");
        if (raw == null || manager == null) return null;
        var accessor = ReflectionSupport.findDeclaredFieldCached(manager.getClass(), "enhancerAccessor")
                .map(field -> {
                    try { return field.get(manager); }
                    catch (ReflectiveOperationException e) { return null; }
                }).orElse(null);
        if (accessor == null) return null;
        var enhancers = call(accessor, "getEnhancers");
        if (!(enhancers instanceof List<?> definitions)) return null;
        var modifiers = new ArrayList<Object>();
        var modifierType = ReflectionSupport.findClassCached(
                "com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceModifier").orElse(null);
        if (modifierType == null) return null;
        for (var enhancer : definitions) {
            var effects = call(enhancer, "effects");
            if (!(effects instanceof List<?> effectList)) return null;
            for (var effect : effectList) {
                if (effect != null && modifierType != null && modifierType.isInstance(effect)) {
                    modifiers.add(effect);
                }
            }
        }
        return ReflectionSupport.findMethodCached(raw.getClass(), "applyModifiers", List.class)
                .flatMap(method -> ReflectionSupport.invoke(method, raw, modifiers)).orElse(null);
    }

    private static BlockPos pedestalPos(BlockPos pos, int slot) {
        return pos.offset(OFFSETS[slot][0], 0, OFFSETS[slot][1]);
    }

    private static ItemStack pedestalStack(@Nullable Object entity) {
        var stack = call(entity, "getStack");
        if (!(stack instanceof ItemStack item)) throw new IllegalStateException("Cannot read pedestal inventory");
        return item;
    }

    private static ItemStack stack(@Nullable Object entity) {
        if (entity == null) throw new IllegalStateException("Forge disappeared");
        var method = ReflectionSupport.findMethodCached(entity.getClass(), "getStack", int.class);
        var value = method.flatMap(found -> ReflectionSupport.invoke(found, entity, 4)).orElse(null);
        if (!(value instanceof ItemStack item)) throw new IllegalStateException("Cannot read forge inventory");
        return item;
    }

    @Nullable private static Object call(@Nullable Object entity, String name) {
        if (entity == null) return null;
        return ReflectionSupport.findMethodCached(entity.getClass(), name)
                .flatMap(method -> ReflectionSupport.invoke(method, entity)).orElse(null);
    }

    private static void mutate(Object entity, String name, Class<?>[] signature, Object... args) {
        ReflectionSupport.invokeMutation(ReflectionSupport.findMethodCached(entity.getClass(), name, signature).orElse(null), entity, args);
    }

    private static int receiptTier(ItemStack stack) {
        return HephaestusUpgradeReceiptItem.tier(stack, PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get());
    }

    @Nullable public static ResourceLocation activeRitualId(ServerLevel level, Object manager) {
        var active = call(manager, "getActiveRitual");
        return active == null ? null : level.registryAccess().registryOrThrow(RITUALS).getKey(active);
    }

    public static void invalidateFailedUpgrade(ServerLevel level, BlockPos pos, Object manager) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || !TILE.equals(entity.getClass().getName())
                || call(entity, "getRitualManager") != manager) return;
        var data = entity.getPersistentData();
        if (!data.contains(PENDING_NONCE)) return;
        data.remove(PENDING_NONCE);
        data.remove(PENDING_RITUAL);
        data.remove(PENDING_TIER);
        if (data.getString(COMPLETED_NONCE).isEmpty()) {
            data.remove(OWNER_DIMENSION);
            data.remove(OWNER_POSITION);
        }
        entity.setChanged();
    }

    private static boolean abandonedProof(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        var data = entity.getPersistentData();
        var dimension = ResourceLocation.tryParse(data.getString(OWNER_DIMENSION));
        if (dimension == null || !data.contains(OWNER_POSITION)) return false;
        var ownerLevel = level.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        if (ownerLevel == null) return false;
        var ownerPos = BlockPos.of(data.getLong(OWNER_POSITION));
        if (!ownerLevel.isLoaded(ownerPos)) return false;
        var owner = ownerLevel.getBlockEntity(ownerPos);
        if (owner instanceof PackagedPatternProviderBlockEntity provider
                && data.getString(COMPLETED_NONCE).equals(provider.getState(pos, proofKey(level)))
                && SlotHarvestOwnership.load(level, pos, provider, CLAIM) != null) return false;
        data.remove(COMPLETED_NONCE);
        data.remove(OWNER_DIMENSION);
        data.remove(OWNER_POSITION);
        entity.setChanged();
        return true;
    }

    public static void markUpgradeCompleted(ServerLevel level, BlockPos pos, Object manager,
            @Nullable ResourceLocation finishedRitual) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || !TILE.equals(entity.getClass().getName())
                || call(entity, "getRitualManager") != manager) return;
        var data = entity.getPersistentData();
        var nonce = data.getString(PENDING_NONCE);
        var ritualId = ResourceLocation.tryParse(data.getString(PENDING_RITUAL));
        if (nonce.isEmpty() || ritualId == null || !ritualId.equals(finishedRitual)
                || data.getInt(PENDING_TIER) != forgeTier(level, pos)) return;
        var result = call(level.registryAccess().registryOrThrow(RITUALS).get(ritualId), "result");
        if (result == null || !UPGRADE.equals(result.getClass().getName())
                || !Integer.valueOf(data.getInt(PENDING_TIER)).equals(call(result, "getUpgradedTier"))) return;
        data.putString(COMPLETED_NONCE, nonce);
        data.remove(PENDING_NONCE);
        data.remove(PENDING_RITUAL);
        data.remove(PENDING_TIER);
        entity.setChanged();
    }

    private static String proofKey(ServerLevel level) {
        return PROOF + "@" + level.dimension().location();
    }

    private static boolean completionProved(ServerLevel level, BlockPos pos, AdapterPersistentScope scope) {
        var nonce = scope.getState(pos, proofKey(level));
        var entity = level.getBlockEntity(pos);
        return nonce != null && !nonce.isEmpty() && entity != null
                && nonce.equals(entity.getPersistentData().getString(COMPLETED_NONCE));
    }

    private static void clearProof(ServerLevel level, BlockPos pos, AdapterPersistentScope scope) {
        var nonce = scope.getState(pos, proofKey(level));
        scope.clearState(pos, proofKey(level));
        var entity = level.getBlockEntity(pos);
        if (nonce == null || entity == null) return;
        var data = entity.getPersistentData();
        boolean matched = nonce.equals(data.getString(COMPLETED_NONCE))
                || nonce.equals(data.getString(PENDING_NONCE));
        if (nonce.equals(data.getString(PENDING_NONCE))) {
            data.remove(PENDING_NONCE);
            data.remove(PENDING_RITUAL);
            data.remove(PENDING_TIER);
        }
        if (nonce.equals(data.getString(COMPLETED_NONCE))) data.remove(COMPLETED_NONCE);
        if (matched) {
            data.remove(OWNER_DIMENSION);
            data.remove(OWNER_POSITION);
        }
        entity.setChanged();
    }

    private static int forgeTier(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!BLOCK.equals(AdapterBlocks.idOf(state))) return 0;
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty tier && "tier".equals(property.getName())) {
                return state.getValue(tier);
            }
        }
        return 0;
    }

    private static boolean upgradeClaimMatches(ServerLevel level, SlotHarvestOwnership.State claim, int tier) {
        var ritual = level.registryAccess().registryOrThrow(RITUALS).get(claim.recipeId());
        var result = call(ritual, "result");
        return result != null && UPGRADE.equals(result.getClass().getName())
                && Integer.valueOf(tier).equals(call(result, "getUpgradedTier"))
                && Integer.valueOf(tier - 1).equals(call(result, "getRequiredTier"));
    }

    private record Ritual(ResourceLocation id, Object value, ItemStack output, int requiredTier) { }
}
