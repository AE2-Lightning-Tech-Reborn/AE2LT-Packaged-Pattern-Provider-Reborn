package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.recipe.CoreUpgradeRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** Routes only the terminal's final core output; intermediate cores stay in the CPU. */
public final class TerminalCoreOrders {
    private TerminalCoreOrders() {}

    /** A single decision powers both the confirmation UI and the actual submission. */
    public record Decision(String mode, PackagedPatternProviderLogic provider, ItemStack required, long amount) {
        public boolean blocked() { return mode.equals("busy") || mode.equals("single_upgrade") || mode.equals("missing_core"); }
        public Component description() {
            if (mode.isEmpty()) return Component.empty();
            var text = Component.translatable("ae2ltpp.terminal." + mode + ".help", amount,
                    required.isEmpty() ? Component.empty() : required.getHoverName());
            if (provider != null) {
                var host = provider.packagedHost();
                text.append("\n").append(Component.translatable("ae2ltpp.terminal.destination",
                        host.getLevel().dimension().location().toString(), host.getBlockPos().toShortString()));
            }
            return text;
        }
    }
    private static final Decision NONE = new Decision("", null, ItemStack.EMPTY, 0);

    public static Decision inspect(ICraftingService service, ICraftingPlan plan) {
        if (plan == null || plan.simulation() || !plan.missingItems().isEmpty()
                || !(service instanceof CraftingService crafting)
                || !(plan.finalOutput().what() instanceof AEItemKey key)
                || !(key.getItem() instanceof MultiblockAdapterItem)
                || plan.finalOutput().amount() <= 0) return NONE;
        long amount = plan.finalOutput().amount();
        Decision blocked = null;
        var candidates = new ArrayList<Decision>();
        for (var entry : plan.patternTimes().entrySet()) {
            var pattern = entry.getKey();
            if (entry.getValue() <= 0 || !key.equals(pattern.getPrimaryOutput().what())) continue;
            // A supplied old core means manufacture to ME, including when the provider slot is empty.
            if (Arrays.stream(pattern.getInputs()).flatMap(input -> Arrays.stream(input.getPossibleInputs()))
                    .anyMatch(input -> input.what() instanceof AEItemKey item
                            && item.getItem() instanceof MultiblockAdapterItem)) continue;
            for (var provider : crafting.getProviders(pattern)) {
                if (!(provider instanceof PackagedPatternProviderLogic logic)) continue;
                var host = logic.packagedHost();
                var required = installedRequirement(logic, pattern);
                if (!required.isEmpty()) {
                    if (amount != 1 || entry.getValue() != 1) return new Decision("single_upgrade", logic, required, amount);
                    if (!ItemStack.matches(host.getInstalledAdapterStack(), required)) {
                        if (blocked == null) blocked = new Decision("missing_core", logic, required, amount);
                        continue;
                    }
                } else if (!host.getInstalledAdapterStack().isEmpty()) continue;
                if (host.getCoreOrder().canAcceptTerminalOrder()) {
                    candidates.add(new Decision(required.isEmpty() ? "install" : "upgrade", logic, required, amount));
                } else {
                    blocked = new Decision("busy", logic, required, amount);
                }
            }
        }
        // Stable routing makes the displayed destination reproducible on multi-provider networks.
        return candidates.stream().sorted(Comparator
                .comparing((Decision d) -> d.provider().packagedHost().getLevel().dimension().location().toString())
                .thenComparingLong(d -> d.provider().packagedHost().getBlockPos().asLong()))
                .findFirst().orElse(blocked != null ? blocked : new Decision("network", null, ItemStack.EMPTY, amount));
    }

    public static ICraftingSubmitResult submit(ICraftingService service, ICraftingPlan plan,
            ICraftingRequester requester, Function<ICraftingRequester, ICraftingSubmitResult> submit) {
        if (requester != null) return submit.apply(requester);
        var decision = inspect(service, plan);
        // The menu rejects these before submitJob and displays the real reason, without a fake CPU error.
        if (decision.blocked()) return CraftingSubmitResult.INCOMPLETE_PLAN;
        return decision.provider() == null ? submit.apply(null)
                : decision.provider().packagedHost().getCoreOrder().submitFromTerminal(plan, submit);
    }

    private static ItemStack installedRequirement(PackagedPatternProviderLogic logic, IPatternDetails pattern) {
        var counters = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < counters.length; i++) {
            var input = pattern.getInputs()[i];
            if (input.getPossibleInputs().length == 0) return ItemStack.EMPTY;
            var stack = input.getPossibleInputs()[0];
            counters[i] = new KeyCounter();
            try {
                counters[i].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
            } catch (ArithmeticException overflow) {
                return ItemStack.EMPTY;
            }
        }
        for (var holder : logic.packagedHost().getLevel().getRecipeManager().getAllRecipesFor(CoreUpgradeRecipes.TYPE.get())) {
            var recipe = holder;
            if (recipe.completionMode(recipe.requiredCore(), pattern, counters) == 0) return recipe.requiredCore();
        }
        return ItemStack.EMPTY;
    }
}
