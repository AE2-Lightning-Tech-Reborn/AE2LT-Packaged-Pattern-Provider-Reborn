package com.moakiee.ae2lt.packaged.client.jei;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Runs the same compiled addon against each selected JEI runtime. Never shipped. */
@JeiPlugin
@EventBusSubscriber(modid = "ae2ltpp", value = Dist.CLIENT)
public final class JeiCompatibilityProbe implements IModPlugin {
    public record Fixture() {}
    private static final Fixture SHARED = new Fixture();
    private static final RecipeType<Fixture> FIRST = RecipeType.create("ae2ltpp", "compat_first", Fixture.class);
    private static final RecipeType<Fixture> SECOND = RecipeType.create("ae2ltpp", "compat_second", Fixture.class);
    private static IJeiRuntime runtime;
    private static int ticks;
    private static int phase;
    private static boolean done;
    private static int extrasCreated;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("ae2ltpp:compat_probe"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    @Override public void onRuntimeUnavailable() { runtime = null; }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(FIRST, 127), new Category(SECOND, 64));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(FIRST, List.of(SHARED));
        registration.addRecipes(SECOND, List.of(SHARED));
    }

    private record Category(RecipeType<Fixture> getRecipeType, int amount) implements IRecipeCategory<Fixture> {
        @Override public Component getTitle() { return Component.literal("JEI compatibility fixture"); }
        @Override public IDrawable getIcon() { return null; }
        @Override public int getWidth() { return 150; }
        @Override public int getHeight() { return 80; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Fixture recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 2, 2).addItemStack(new ItemStack(Items.DIAMOND, amount))
                    .addOptionalTypedIngredients(List.of(Optional.empty()));
            builder.addSlot(RecipeIngredientRole.INPUT, 20, 2).addFluidStack(Fluids.WATER, 619400);
            builder.addSlot(RecipeIngredientRole.INPUT, 38, 2).addIngredient(PackagedResourceIngredients.TYPE,
                    new PackagedResourceIngredients.Resource(ForbiddenEssenceResources.BLOOD, 250));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 112, 2).addItemStack(PPItems.BASIC_PACKAGED_CORE.toStack());
            for (int index = 0; index < 22; index++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 2, 40).addItemStack(new ItemStack(Items.IRON_INGOT));
            }
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, Fixture recipe, IFocusGroup focuses) {
            extrasCreated++;
            builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.INPUT), 2, 1).setPosition(1, 1);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done || ++ticks % 20 != 0) return;
        var mc = Minecraft.getInstance();
        if (ticks > 1800) { finish("FAIL: timeout phase=" + phase); return; }
        if (mc.player == null || runtime == null) return;
        try {
            var manager = runtime.getRecipeManager();
            var cores = manager.createRecipeLookup(PackagedCoreJeiPlugin.TYPE).get().toList();
            if (cores.isEmpty()) return;
            if (phase == 0) {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var fixtures = cores.stream().filter(r -> r.nativeRecipe() == SHARED).toList();
                check(fixtures.size() == 2, "shared recipe mirrored separately for both native categories");
                check(extrasCreated >= 2, "lazy native extras initialized before snapshot");
                for (var recipe : fixtures) {
                    check(recipe.slots().size() == 26, "widget slots remain in the transfer view");
                    var ingredients = recipe.slots().stream().flatMap(s -> s.ingredients().stream()).toList();
                    check(ingredients.stream().noneMatch(java.util.Objects::isNull), "blank alternatives filtered");
                    int expected = recipe.category().getRecipeType().equals(FIRST) ? 127 : 64;
                    check(ingredients.stream().map(ITypedIngredient::getIngredient).anyMatch(i -> i instanceof ItemStack item
                            && item.is(Items.DIAMOND) && item.getCount() == expected), "item quantity retained");
                    check(ingredients.stream().map(ITypedIngredient::getIngredient).anyMatch(i -> i instanceof FluidStack fluid
                            && fluid.getAmount() == 619400), "fluid quantity retained");
                    check(ingredients.stream().map(ITypedIngredient::getIngredient).anyMatch(i -> i instanceof PackagedResourceIngredients.Resource resource
                            && resource.amount() == 250), "custom resource quantity retained");
                }
                var resource = new PackagedResourceIngredients.Resource(ForbiddenEssenceResources.BLOOD, 250);
                var ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
                var encoded = PackagedResourceIngredients.CODEC.encodeStart(ops, resource).getOrThrow();
                check(resource.equals(PackagedResourceIngredients.CODEC.parse(ops, encoded).getOrThrow()), "resource bookmark codec round trip");
                var converter = tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters.getConverter(PackagedResourceIngredients.TYPE);
                check(converter != null && converter.getStackFromIngredient(resource).amount() == 250, "AE2 converter retains amount");
                check(cores.stream().anyMatch(PackagedCoreJeiPlugin.CoreRecipe::installedUpgrade), "installed upgrade registered");
                check(cores.stream().anyMatch(r -> r.category() == null && !r.installedUpgrade()), "spare core recipe registered");
                var category = manager.getRecipeCategory(PackagedCoreJeiPlugin.TYPE);
                check(category.getWidth() == 176 && category.getHeight() == 140, "compact fixed recipe dimensions");
                var layout = manager.createRecipeLayoutDrawable(category, fixtures.getFirst(), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                layout.tick();
                var screen = mc.screen;
                check(layout.getInputHandler().handleInput(3, category.getHeight() - 40, new SimulatedClick()), "native recipe link accepts simulated click");
                check(mc.screen == screen, "simulated click does not navigate");
                runtime.getRecipesGui().showRecipes(category, List.of(fixtures.getFirst()), List.of());
            } else {
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(mc.gameDirectory.toPath().resolve("core-recipe.png"));
                }
                String version = ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString();
                finish("PASS: JEI " + version + "; native widgets, blank alternatives, quantities, category identity, resource codec/converter, upgrades and simulated click");
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish("FAIL: " + failure);
        }
    }

    private record SimulatedClick() implements IJeiUserInput {
        @Override public InputConstants.Key getKey() { return InputConstants.Type.MOUSE.getOrCreate(0); }
        @Override public int getModifiers() { return 0; }
        @Override public boolean isSimulate() { return true; }
        @Override public boolean is(KeyMapping mapping) { return false; }
        @Override public boolean is(IJeiKeyMapping mapping) { return false; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void finish(String result) {
        done = true;
        var mc = Minecraft.getInstance();
        try { Files.writeString(mc.gameDirectory.toPath().resolve("client-result.txt"), result); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
