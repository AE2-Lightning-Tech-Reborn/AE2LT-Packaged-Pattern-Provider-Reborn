package com.moakiee.ae2lt.packaged.item;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.resonant.ResonantLayout;
import com.moakiee.ae2lt.packaged.registry.PPDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ResonantPackagedCoreItem extends MultiblockAdapterItem {
    public ResonantPackagedCoreItem(Properties properties) {
        super(properties, AdapterIds.RESONANT, Set.of(AdapterIds.RESONANT));
    }

    @Nullable
    public static ResonantLayout layout(ItemStack stack) {
        return stack.getItem() instanceof ResonantPackagedCoreItem
                ? stack.get(PPDataComponents.RESONANT_LAYOUT.get()) : null;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.mayBuild()) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // A second core can copy a configured provider without moving its original anchor.
        if (!player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof PackagedPatternProviderBlockEntity provider) {
            var installed = layout(provider.getInstalledAdapterStack());
            if (installed != null) {
                stack.set(PPDataComponents.RESONANT_LAYOUT.get(), installed);
                message(player, "copied");
                return InteractionResult.CONSUME;
            }
        }
        var old = layout(stack);
        if (old == null || player.isShiftKeyDown()) {
            if (level.getBlockEntity(pos) instanceof PackagedPatternProviderBlockEntity) {
                message(player, "machine_anchor");
                return InteractionResult.CONSUME;
            }
            var state = level.getBlockState(pos);
            var bound = new ResonantLayout(GlobalPos.of(level.dimension(), pos),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    ResonantLayout.forward(state, context.getClickedFace()), context.getClickedFace(), 0, List.of());
            stack.set(PPDataComponents.RESONANT_LAYOUT.get(), bound);
            message(player, "anchor", pos.getX(), pos.getY(), pos.getZ());
            return InteractionResult.CONSUME;
        }
        if (!old.source().dimension().equals(level.dimension())) {
            message(player, "same_dimension");
            return InteractionResult.CONSUME;
        }
        var target = new ResonantLayout.Target(pos.subtract(old.source().pos()), context.getClickedFace(),
                Optional.of(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())));
        var changed = old.withTarget(old.selected(), old.target(old.selected()).filter(target::equals).isPresent()
                ? null : target);
        stack.set(PPDataComponents.RESONANT_LAYOUT.get(), changed);
        showSelection(player, changed);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            var current = layout(stack);
            if (current == null) message(player, "unbound");
            else {
                var next = current.select(current.selected() < ResonantLayout.INPUTS ? ResonantLayout.INPUTS : 0);
                stack.set(PPDataComponents.RESONANT_LAYOUT.get(), next);
                showSelection(player, next);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static void cycle(Player player, boolean next) {
        var stack = player.getMainHandItem();
        var current = layout(stack);
        if (current == null || !player.mayBuild() || !player.isShiftKeyDown()) return;
        var selected = current.cycle(next);
        stack.set(PPDataComponents.RESONANT_LAYOUT.get(), selected);
        showSelection(player, selected);
    }

    private static Component selection(ResonantLayout layout) {
        boolean input = layout.selected() < ResonantLayout.INPUTS;
        return Component.translatable("ae2ltpp.resonant." + (input ? "input" : "output"),
                layout.selected() - (input ? 0 : ResonantLayout.INPUTS) + 1,
                input ? ResonantLayout.INPUTS : ResonantLayout.OUTPUTS);
    }

    private static void showSelection(Player player, ResonantLayout layout) {
        var target = layout.target(layout.selected());
        if (target.isEmpty()) message(player, "selected_empty", selection(layout));
        else {
            var t = target.get();
            message(player, "selected", selection(layout), t.offset().getX(), t.offset().getY(),
                    t.offset().getZ(), Component.translatable("ae2ltpp.resonant.face." + t.face().getName()));
        }
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("ae2ltpp.resonant." + key, args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ae2ltpp.resonant.bind_hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ae2ltpp.resonant.select_hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ae2ltpp.resonant.copy_hint").withStyle(ChatFormatting.GRAY));
        var layout = layout(stack);
        if (layout == null) tooltip.add(Component.translatable("ae2ltpp.resonant.unbound").withStyle(ChatFormatting.YELLOW));
        else {
            var pos = layout.source().pos();
            tooltip.add(Component.translatable("ae2ltpp.resonant.source", pos.getX(), pos.getY(), pos.getZ(),
                    layout.source().dimension().location().toString()).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(selection(layout).copy().withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("ae2ltpp.resonant.bindings", layout.bindings().size())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
