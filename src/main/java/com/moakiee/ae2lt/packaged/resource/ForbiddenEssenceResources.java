package com.moakiee.ae2lt.packaged.resource;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class ForbiddenEssenceResources {
    public static final PackagedResourceKey AUREAL = key("aureal");
    public static final PackagedResourceKey SOULS = key("souls");
    public static final PackagedResourceKey BLOOD = key("blood");
    public static final PackagedResourceKey EXPERIENCE = key("experience");
    public static final List<PackagedResourceKey> ALL = List.of(AUREAL, SOULS, BLOOD, EXPERIENCE);

    private ForbiddenEssenceResources() {}
    private static PackagedResourceKey key(String path) {
        return PackagedResourceKey.of(new ResourceLocation("forbidden_arcanus", path));
    }
    public static boolean isEssence(PackagedResourceKey key) { return ALL.contains(key); }
}
