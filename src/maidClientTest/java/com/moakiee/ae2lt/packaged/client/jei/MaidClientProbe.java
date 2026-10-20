package com.moakiee.ae2lt.packaged.client.jei;

import java.nio.file.Files;
import java.util.List;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.MaidPowerKey;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="ae2ltpp", value=Dist.CLIENT)
public final class MaidClientProbe {
    static int phase,ticks; static boolean done;
    static IJeiRuntime runtime() throws Exception {var f=PackagedCoreJeiPlugin.class.getDeclaredField("runtime");f.setAccessible(true);return (IJeiRuntime)f.get(null);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();if(done||++ticks%40!=0)return;
        if(ticks>1800){finish("FAIL timeout phase="+phase);return;}
        if(mc.player==null)return;
        try {
            var runtime=runtime();if(runtime==null)return;var manager=runtime.getRecipeManager();
            switch(phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Maid / Raven packaged core QA");mc.options.guiScale().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
                    var converter=tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters.getConverter(PackagedResourceIngredients.TYPE);
                    check(converter!=null,"P JEI converter registered");
                    var point=converter.getStackFromIngredient(new PackagedResourceIngredients.Resource(MaidPowerKey.INSTANCE,60));
                    check(point.what()==MaidPowerKey.INSTANCE&&point.amount()==60,"0.6 P transfers as 60 raw units");
                    var recipes=manager.createRecipeLookup(PackagedCoreJeiPlugin.TYPE).get().toList();
                    var raven=recipes.stream().filter(r->r.result().is(PPItems.APOTHIC_INFUSION_ADAPTER.get())).findFirst().orElseThrow();
                    check(raven.category().getRecipeType().equals(RavenInfusionJeiRecipes.TYPE),"raven exclusive core category");
                    check(raven.slots().stream().filter(s->s.role()==RecipeIngredientRole.INPUT).flatMap(s->s.ingredients().stream()).anyMatch(i->i.getIngredient() instanceof net.minecraft.world.item.ItemStack item&&item.is(PPItems.BASIC_PACKAGED_CORE.get())&&item.getCount()==1),"raven basic core input exposed");
                    check(raven.slots().stream().filter(s->s.role()==RecipeIngredientRole.INPUT).flatMap(s->s.ingredients().stream()).anyMatch(i->i.getIngredient() instanceof net.neoforged.neoforge.fluids.FluidStack f&&f.getAmount()==619400),"full 100-level fluid ingredient exposed");
                    var maid=recipes.stream().filter(r->r.result().is(PPItems.MAID_ALTAR_ADAPTER.get())).findFirst().orElseThrow();
                    check(maid.slots().stream().flatMap(s->s.ingredients().stream()).anyMatch(i->i.getIngredient() instanceof PackagedResourceIngredients.Resource p&&p.key()==MaidPowerKey.INSTANCE&&p.amount()==100),"maid core includes 1 P");
                    runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(PackagedCoreJeiPlugin.TYPE),List.of(raven),List.of());
                }
                case 1 -> {
                    capture("01-raven-core.png");var recipe=manager.createRecipeLookup(RavenInfusionJeiRecipes.TYPE).get().filter(r->!r.output().is(PPItems.APOTHIC_INFUSION_ADAPTER.get())).findFirst().orElseThrow();
                    check(recipe.fluid()>0,"ordinary native infusion cost");runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(RavenInfusionJeiRecipes.TYPE),List.of(recipe),List.of());
                }
                case 2 -> {
                    capture("02-raven-infusion.png");var recipe=manager.createRecipeLookup(MaidAltarJeiRecipes.TYPE).get().filter(r->r.output().what()==MaidPowerKey.INSTANCE).findFirst().orElseThrow();
                    check(recipe.output().amount()==60,"fixed 0.6 P recipe");runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(MaidAltarJeiRecipes.TYPE),List.of(recipe),List.of());
                }
                case 3 -> {
                    capture("03-maid-conversion.png");mc.setScreen(new Screen(Component.literal("Packaged resources")) {
                        @Override public void render(GuiGraphics g,int x,int y,float partial) {
                            super.render(g,x,y,partial);int sx=width/2-80,sy=height/2-35;
                            g.renderItem(PPItems.APOTHIC_INFUSION_ADAPTER.toStack(),sx,sy);g.drawString(font,PPItems.APOTHIC_INFUSION_ADAPTER.get().getName(PPItems.APOTHIC_INFUSION_ADAPTER.toStack()),sx+24,sy+4,0xFFFFFF);
                            g.renderItem(PPItems.MAID_ALTAR_ADAPTER.toStack(),sx,sy+25);g.drawString(font,PPItems.MAID_ALTAR_ADAPTER.get().getName(PPItems.MAID_ALTAR_ADAPTER.toStack()),sx+24,sy+29,0xFFFFFF);
                            AEKeyRendering.drawInGui(mc,g,sx,sy+50,MaidPowerKey.INSTANCE);g.drawString(font,"0.6 P",sx+24,sy+54,0xFFFFFF);
                        }
                    });
                }
                case 4 -> {capture("04-core-icons-and-p.png");finish("PASS: raven-only 619400 mB core, native infusion, maid 0.6 P conversion, P ghost converter and models");}
            }
            phase++;
        }catch(Throwable e){e.printStackTrace();finish("FAIL phase="+phase+" "+e);}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void capture(String name)throws Exception{var mc=Minecraft.getInstance();try(var im=Screenshot.takeScreenshot(mc.getMainRenderTarget())){im.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    static void finish(String result){done=true;var mc=Minecraft.getInstance();try{Files.writeString(mc.gameDirectory.toPath().resolve("client-result.txt"),result);}catch(Exception e){e.printStackTrace();}mc.stop();}
}
