package com.moakiee.ae2lt.packaged.mixin;

import java.util.Map;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.moakiee.ae2lt.packaged.compat.MalumRecipeAdvancementCompat;

@Mixin(ServerAdvancementManager.class)
public abstract class MalumRecipeAdvancementMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void ae2ltpp$restoreMissingMalumRecipeCriteria(Map<ResourceLocation, JsonElement> resources,
                                                         ResourceManager resourceManager, ProfilerFiller profiler,
                                                         CallbackInfo callback) {
        if (ModList.get().isLoaded("malum")) {
            resources.replaceAll(MalumRecipeAdvancementCompat::repair);
        }
    }
}
