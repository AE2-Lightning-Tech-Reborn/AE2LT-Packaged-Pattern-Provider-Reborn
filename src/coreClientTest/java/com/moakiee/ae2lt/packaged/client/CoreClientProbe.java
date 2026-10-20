package com.moakiee.ae2lt.packaged.client;
import java.nio.file.Files;
import java.util.List;
import appeng.api.client.AEKeyRendering;
import appeng.core.definitions.AEBlocks;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.client.jei.*;
import com.moakiee.ae2lt.packaged.registry.*;
import com.moakiee.ae2lt.packaged.resource.*;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="ae2ltpp", value=Dist.CLIENT)
public final class CoreClientProbe {
    static int phase,ticks; static boolean done; static volatile Throwable serverError;
    static final BlockPos POS = new BlockPos(0,100,0);
    static IJeiRuntime runtime() throws Exception {
        var field = PackagedCoreJeiPlugin.class.getDeclaredField("runtime");field.setAccessible(true);return (IJeiRuntime)field.get(null);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(done || ++ticks%40!=0) return;
        if(ticks>2400){report("FAIL: client timeout phase="+phase);done=true;mc.stop();return;}
        if(mc.player==null || mc.getSingleplayerServer()==null) return;
        try {
            if(serverError!=null)throw new AssertionError(serverError);
            if(runtime()==null) {check(ticks<2000,"JEI runtime timeout");return;}
            switch(phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Packaged core QA - disposable world");mc.getWindow().setWindowed(1200,900);
                    mc.options.guiScale().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
                    server(()->{
                        var level=mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(POS,PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(POS.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.teleportTo(level,0.5,101,2.5,java.util.Set.of(),180,10);

                    });
                }
                case 1 -> {
                    if(!(mc.level.getBlockEntity(POS) instanceof PackagedPatternProviderBlockEntity)){phase--;break;}
                    server(()->{
                        var host=(PackagedPatternProviderBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(POS);
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        host.openMenu(player,MenuLocators.forBlockEntity(host));
                    });
                }
                case 2 -> {
                    check(mc.screen instanceof PackagedPatternProviderScreen,"provider screen");capture("01-provider.png");
                    var button=mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals("核心制作")).map(w->(Button)w).findFirst().orElseThrow();
                    mc.screen.mouseClicked(button.getX()+2,button.getY()+2,0);
                }
                case 3 -> {
                    var manager=runtime().getRecipeManager();
                    var all=manager.createRecipeLookup(PackagedCoreJeiPlugin.TYPE).get().toList();
                    check(all.size()>=11,"native + upgrade pages registered: "+all.size());
                    var upgrade=all.stream().filter(r->r.category()==null).findFirst().orElseThrow();
                    check(upgrade.slots().stream().filter(s->s.role()==RecipeIngredientRole.CATALYST).count()==1,"installed core is a catalyst");
                    check(upgrade.slots().stream().filter(s->s.role()==RecipeIngredientRole.INPUT).flatMap(s->s.ingredients().stream()).noneMatch(i->i.getIngredient() instanceof net.minecraft.world.item.ItemStack stack && stack.getItem() instanceof com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem),"old core omitted from transferred inputs");
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(PackagedCoreJeiPlugin.TYPE),List.of(upgrade),List.of());
                }
                case 4 -> {
                    capture("02-core-upgrade-jei.png");
                    var manager=runtime().getRecipeManager();
                    var first=manager.createRecipeLookup(PackagedCoreJeiPlugin.TYPE).get().filter(r->r.result().is(PPItems.EC_BASIC_ADAPTER.get())).findFirst().orElseThrow();
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(PackagedCoreJeiPlugin.TYPE),List.of(first),List.of());
                }
                case 5 -> {
                    capture("03-first-core-jei.png");
                    var converter=tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters.getConverter(PackagedResourceIngredients.TYPE);
                    check(converter!=null,"resource ingredient converter registered");
                    var stack=converter.getStackFromIngredient(new PackagedResourceIngredients.Resource(ForbiddenEssenceResources.BLOOD,250));
                    check(stack.what().equals(ForbiddenEssenceResources.BLOOD)&&stack.amount()==250,"resource ghost transfer keeps identity and quantity");
                    mc.setScreen(new Screen(Component.literal("Resources")) {
                        @Override public void render(GuiGraphics g,int x,int y,float partial) {
                            super.render(g,x,y,partial);
                            int sy=height/2-50;
                            for(var key:ForbiddenEssenceResources.ALL) {
                                AEKeyRendering.drawInGui(mc,g,width/2-70,sy,key);
                                g.drawString(font,key.getDisplayName(),width/2-44,sy+4,0xFFFFFF);sy+=26;
                            }
                        }
                    });
                }
                case 6 -> {
                    capture("04-resource-keys.png");
                    var type=mezz.jei.api.recipe.RecipeType.create("ae2ltpp","fa_processing",com.moakiee.ae2lt.packaged.logic.multiblock.fa.FARecipeViews.View.class);
                    var manager=runtime().getRecipeManager();
                    var all=manager.createRecipeLookup(type).get().toList();
                    check(all.stream().anyMatch(r->r.kind().equals("conversion")),"FA native conversion recipes registered");
                    var ritual=all.stream().filter(r->r.outputs().stream().anyMatch(o->o.what().equals(appeng.api.stacks.AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get())))).findFirst().orElseThrow();
                    check(ritual.inputs().stream().filter(options->options.getFirst().what() instanceof PackagedResourceKey).count()==4,"all four ritual essence inputs exposed");
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(type),List.of(ritual),List.of());
                }
                case 7 -> {
                    capture("05-fa-ritual-jei.png");
                    var type=mezz.jei.api.recipe.RecipeType.create("ae2ltpp","fa_processing",com.moakiee.ae2lt.packaged.logic.multiblock.fa.FARecipeViews.View.class);
                    var manager=runtime().getRecipeManager();
                    var recipe=manager.createRecipeLookup(type).get().filter(r->r.kind().equals("conversion")&&r.outputs().getFirst().what().equals(ForbiddenEssenceResources.AUREAL)).findFirst().orElseThrow();
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(type),List.of(recipe),List.of());
                }
                case 8 -> {
                    capture("06-fa-conversion-jei.png");
                    var type=mezz.jei.api.recipe.RecipeType.create("ae2ltpp","fa_processing",com.moakiee.ae2lt.packaged.logic.multiblock.fa.FARecipeViews.View.class);
                    var manager=runtime().getRecipeManager();
                    var recipe=manager.createRecipeLookup(type).get().filter(r->r.kind().equals("black_hole")).findFirst().orElseThrow();
                    check(recipe.inputs().getFirst().getFirst().amount()==1200,"black hole amount displayed");
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(type),List.of(recipe),List.of());
                }
                case 9 -> {capture("07-fa-black-hole-jei.png");report("PASS: provider core UI and resource converter; FA ritual essence slots, native conversion and black hole fluid amounts");done=true;mc.stop();}
            }
            phase++;
        } catch(Throwable e) {e.printStackTrace();report("FAIL phase="+phase+" "+e);done=true;mc.stop();}
    }
    static void server(Runnable r){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable e){serverError=e;}});}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void capture(String name)throws Exception{var mc=Minecraft.getInstance();try(var im=Screenshot.takeScreenshot(mc.getMainRenderTarget())){im.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    static void report(String s){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("client-result.txt"),s);}catch(Exception e){e.printStackTrace();}}
}
