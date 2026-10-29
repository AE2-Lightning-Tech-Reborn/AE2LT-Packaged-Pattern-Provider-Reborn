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
                    check(mc.screen.children().stream().noneMatch(w -> w instanceof Button b
                            && (b.getMessage().getString().equals("核心制作") || b.getMessage().getString().equals("×"))),
                            "provider no longer has core order or cancel buttons");
                    runtime().getRecipesGui().showTypes(List.of(PackagedCoreJeiPlugin.TYPE));
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
                case 9 -> {
                    capture("07-fa-black-hole-jei.png");
                    var manager=runtime().getRecipeManager();
                    var spare=manager.createRecipeLookup(PackagedCoreJeiPlugin.TYPE).get()
                            .filter(r->r.category()==null && !r.installedUpgrade()).findFirst().orElseThrow();
                    check(spare.slots().stream().noneMatch(slot->slot.role()==RecipeIngredientRole.CATALYST),"spare recipe has no installed requirement");
                    check(spare.slots().stream().filter(slot->slot.role()==RecipeIngredientRole.INPUT)
                            .flatMap(slot->slot.ingredients().stream()).anyMatch(i->i.getIngredient() instanceof net.minecraft.world.item.ItemStack item
                            && item.getItem() instanceof com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem),"spare recipe transfers old core as a real input");
                    runtime().getRecipesGui().showRecipes(manager.getRecipeCategory(PackagedCoreJeiPlugin.TYPE),List.of(spare),List.of());
                }
                case 10 -> {
                    capture("08-spare-core-jei.png");
                    server(()->{
                        var level=mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(POS.north(),AEBlocks.DRIVE.block().defaultBlockState());
                        level.setBlockAndUpdate(POS.south(),AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
                        ((appeng.blockentity.storage.DriveBlockEntity)level.getBlockEntity(POS.north())).getInternalInventory()
                                .setItemDirect(0,appeng.core.definitions.AEItems.ITEM_CELL_64K.stack());
                        var host=(PackagedPatternProviderBlockEntity)level.getBlockEntity(POS);
                        host.getAdapterInv().setItemDirect(0,PPItems.EC_BASIC_ADAPTER.toStack());
                        var inputs=List.of(material("luminessence",4),material("advanced_component",2),material("black_iron_ingot",2));
                        host.getLogic().getPatternInv().setItemDirect(0,appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(inputs,
                                List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),1))));
                    });
                }
                case 11 -> server(()->{
                    var host=(PackagedPatternProviderBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(POS);
                    host.getLogic().updatePatterns();
                    var storage=host.getMainNode().getGrid().getStorageService().getInventory();
                    for(var input:List.of(material("luminessence",12),material("advanced_component",6),material("black_iron_ingot",6)))
                        storage.insert(input.what(),input.amount(),appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    appeng.menu.MenuOpener.open(appeng.menu.me.crafting.CraftConfirmMenu.TYPE,player,MenuLocators.forBlockEntity(host));
                    ((appeng.menu.me.crafting.CraftConfirmMenu)player.containerMenu).planJob(appeng.api.stacks.AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),1,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                });
                case 12 -> { checkConfirmation("upgrade",false); hoverStart(); }
                case 13 -> {
                    capture("09-terminal-upgrade.png");
                    server(()->serverMenu().planJob(appeng.api.stacks.AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),3,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS));
                }
                case 14 -> { checkConfirmation("single_upgrade",true); hoverStart(); }
                case 15 -> {
                    capture("10-terminal-upgrade-quantity.png");
                    server(()->{
                        ((PackagedPatternProviderBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(POS)).getAdapterInv().setItemDirect(0,net.minecraft.world.item.ItemStack.EMPTY);
                        serverMenu().planJob(appeng.api.stacks.AEItemKey.of(PPItems.EC_ADVANCED_ADAPTER.get()),1,
                                appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                    });
                }
                case 16 -> { checkConfirmation("missing_core",true); hoverStart(); }
                case 17 -> {
                    capture("11-terminal-missing-core.png");
                    server(()->((PackagedPatternProviderBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(POS)).getAdapterInv().setItemDirect(0,PPItems.EC_BASIC_ADAPTER.toStack()));
                }
                case 18 -> {
                    checkConfirmation("upgrade",false); // Fixing the provider live must unblock the same confirmation.
                    var menu=(appeng.menu.me.crafting.CraftConfirmMenu)mc.player.containerMenu;
                    mc.setScreen(new appeng.client.gui.me.crafting.CraftConfirmScreen(menu,mc.player.getInventory(),Component.literal("Core QA"),
                            appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/craft_confirm.json")) {
                        @Override protected void updateBeforeRender() {
                            super.updateBeforeRender();
                            // A replacement report can reset start after super, as LT's current screen does.
                            ((com.moakiee.ae2lt.packaged.mixin.client.CraftConfirmScreenAccessor)this).ae2ltpp$startButton().active = !menu.hasNoCPU();
                        }
                    });
                }
                case 19 -> {
                    checkConfirmation("upgrade",false);
                    server(()->((PackagedPatternProviderBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(POS)).getAdapterInv().setItemDirect(0,net.minecraft.world.item.ItemStack.EMPTY));
                }
                case 20 -> { checkConfirmation("missing_core",true); hoverStart(); }
                case 21 -> {
                    capture("12-subclass-terminal-blocked.png");
                    report("PASS: provider UI; JEI upgrade/spare inputs; native confirmation label, tooltip, quantity/missing-core rejection and live recovery; subclass button-reset regression; FA resources");done=true;mc.stop();
                }
            }
            phase++;
        } catch(Throwable e) {e.printStackTrace();report("FAIL phase="+phase+" "+e);done=true;mc.stop();}
    }
    static appeng.api.stacks.GenericStack material(String name,long count) {
        return new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse("extendedcrafting:"+name))),count);
    }
    static appeng.menu.me.crafting.CraftConfirmMenu serverMenu() {
        var mc=Minecraft.getInstance();
        return (appeng.menu.me.crafting.CraftConfirmMenu)mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).containerMenu;
    }
    static void checkConfirmation(String mode,boolean blocked) {
        var mc=Minecraft.getInstance();
        check(mc.screen instanceof appeng.client.gui.me.crafting.CraftConfirmScreen,"real crafting confirmation screen");
        var state=(com.moakiee.ae2lt.packaged.menu.CoreOrderConfirmation)mc.player.containerMenu;
        check(state.ae2ltpp$coreMode().equals(mode),"server-synced mode="+state.ae2ltpp$coreMode()+" expected="+mode);
        var start=((com.moakiee.ae2lt.packaged.mixin.client.CraftConfirmScreenAccessor)mc.screen).ae2ltpp$startButton();
        check(start.active!=blocked,"start availability follows core state");
        check(start.getMessage().equals(Component.translatable("ae2ltpp.terminal."+mode)),"action label follows core state");
        check(start.getTooltip()!=null,"destination/recovery tooltip exists");
        check(mc.font.width(start.getMessage())+12<=start.getWidth(),"label fits button");
        check(state.ae2ltpp$coreDescription().getString().contains("0, 100, 0"),"provider coordinates synchronized");
    }
    static void hoverStart() throws Exception {
        var mc=Minecraft.getInstance();
        var start=((com.moakiee.ae2lt.packaged.mixin.client.CraftConfirmScreenAccessor)mc.screen).ae2ltpp$startButton();
        double scaleX=(double)mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth();
        double scaleY=(double)mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
        // Drive the test cursor directly; GLFW ignores warps when this disposable window is unfocused.
        var x=net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");x.setAccessible(true);
        var y=net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");y.setAccessible(true);
        x.setDouble(mc.mouseHandler,(start.getX()+start.getWidth()/2.0)*scaleX);
        y.setDouble(mc.mouseHandler,(start.getY()+start.getHeight()/2.0)*scaleY);
        // AbstractWidget.isMouseOver intentionally returns false for disabled buttons.
        double guiX=mc.mouseHandler.xpos()/scaleX,guiY=mc.mouseHandler.ypos()/scaleY;
        check(guiX>=start.getX() && guiX<start.getX()+start.getWidth()
                && guiY>=start.getY() && guiY<start.getY()+start.getHeight(),"test cursor is inside the action button bounds");
    }
    static void server(Runnable r){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable e){serverError=e;}});}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void capture(String name)throws Exception{var mc=Minecraft.getInstance();try(var im=Screenshot.takeScreenshot(mc.getMainRenderTarget())){im.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    static void report(String s){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("client-result.txt"),s);}catch(Exception e){e.printStackTrace();}}
}
