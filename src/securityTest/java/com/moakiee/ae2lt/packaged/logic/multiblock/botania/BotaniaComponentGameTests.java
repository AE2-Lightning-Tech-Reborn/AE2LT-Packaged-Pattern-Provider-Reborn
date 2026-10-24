package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.util.*;
import java.lang.reflect.Method;
import appeng.api.stacks.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class BotaniaComponentGameTests {
    @GameTest(template = "resonant_test")
    public static void componentDistinctIngredientsAreNeverPooledOrMaterializedAsEachOther(GameTestHelper h) throws Exception {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("special"));
        var namedKey = AEItemKey.of(named);
        var plainKey = AEItemKey.of(Items.IRON_INGOT);
        var counter = new KeyCounter(); counter.add(namedKey, 1); counter.add(plainKey, 1);
        var inputs = new KeyCounter[] {counter};
        var strict = DataComponentIngredient.of(true, named);
        var requiresTwoSpecial = List.of(strict, strict);
        var generic = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT));
        for (var type : List.of(TerraPlateAdapter.class, RunicAltarAdapter.class)) {
            var pick = method(type, "pickKeyCounterStacks", List.class, KeyCounter[].class);
            h.assertTrue(pick.invoke(null, requiresTwoSpecial, inputs) == null, "one special cannot satisfy two strict inputs: " + type);
            @SuppressWarnings("unchecked") var stacks = (List<ItemStack>) pick.invoke(null, generic, inputs);
            h.assertTrue(stacks != null && stacks.size() == 2, "generic ingredients accept both concrete variants");
            var actual = new HashSet<AEItemKey>(); stacks.forEach(stack -> actual.add(AEItemKey.of(stack)));
            h.assertTrue(actual.equals(Set.of(namedKey, plainKey)), "physical materialization preserves each actual component set");
        }
        var portal = method(AlfheimPortalAdapter.class, "consumeIngredientsStrict", List.class, KeyCounter[].class);
        h.assertTrue(!(boolean) portal.invoke(null, requiresTwoSpecial, inputs), "virtual portal cannot borrow another variant's count");
        h.assertTrue((boolean) portal.invoke(null, generic, inputs), "ordinary portal ingredient behavior retained");
        var petal = method(PetalApothecaryAdapter.class, "consumeIngredientsStrict", List.class, long.class, LinkedHashMap.class);
        var available = new LinkedHashMap<AEItemKey, Long>(); available.put(namedKey, 1L); available.put(plainKey, 1L);
        h.assertTrue(!(boolean) petal.invoke(null, List.of(strict), 2L, new LinkedHashMap<>(available)), "virtual petal batch tests each variant");
        h.assertTrue((boolean) petal.invoke(null, List.of(Ingredient.of(Items.IRON_INGOT)), 2L, available)
                && available.isEmpty(), "batch can combine different variants when the native ingredient accepts both");
        h.succeed();
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        var method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
}
