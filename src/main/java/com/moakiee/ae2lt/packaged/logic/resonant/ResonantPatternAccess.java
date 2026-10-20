package com.moakiee.ae2lt.packaged.logic.resonant;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Optional AE2CS access: no upstream classes are linked when Crystal Science is absent. */
public final class ResonantPatternAccess {
    private static final ResourceLocation COMPONENT = ResourceLocation.parse("ae2cs:encoded_resonating_pattern");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ResonantPatternAccess.class);
    private static boolean warned;

    public record AbsoluteTarget(GlobalPos pos, Direction face) {}
    public record Data(List<GenericStack> inputs, List<Optional<AbsoluteTarget>> targets) {
        public Data {
            // Sparse AE2 inputs deliberately contain nulls.
            inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
            targets = List.copyOf(targets);
            if (inputs.size() != targets.size() || inputs.size() > ResonantLayout.INPUTS) {
                throw new IllegalArgumentException("Invalid resonant input layout size");
            }
        }
    }

    private record Accessors(Method inputs, Method targets) {}
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<>() {
        @Override protected Accessors computeValue(Class<?> type) {
            try { return new Accessors(type.getMethod("sparseInputs"), type.getMethod("inputTargets")); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unsupported AE2CS pattern", e); }
        }
    };
    private record TargetAccessors(Method pos, Method face) {}
    private static final ClassValue<TargetAccessors> TARGET_ACCESSORS = new ClassValue<>() {
        @Override protected TargetAccessors computeValue(Class<?> type) {
            try { return new TargetAccessors(type.getMethod("pos"), type.getMethod("face")); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unsupported AE2CS target", e); }
        }
    };

    private ResonantPatternAccess() {}

    @Nullable
    public static Data read(IPatternDetails pattern) {
        if (pattern == null || pattern.getDefinition() == null) return null;
        var definition = pattern.getDefinition();
        try {
            if (BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(COMPONENT)) {
                @SuppressWarnings("unchecked")
                var type = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(COMPONENT);
                var encoded = definition.get(type);
                if (encoded != null) return readResonant(encoded);
            }
            var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
            if (encoded == null) return null;
            return new Data(encoded.sparseInputs(), Collections.nCopies(encoded.sparseInputs().size(), Optional.empty()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                LOG.warn("Cannot read resonant routing data; refusing this pattern", failure);
            }
            return null;
        }
    }

    private static Data readResonant(Object encoded) throws ReflectiveOperationException {
        var access = ACCESSORS.get(encoded.getClass());
        var inputs = new ArrayList<GenericStack>();
        for (var entry : (List<?>) access.inputs().invoke(encoded)) inputs.add((GenericStack) entry);
        var targets = new ArrayList<Optional<AbsoluteTarget>>();
        for (var entry : (List<?>) access.targets().invoke(encoded)) {
            var optional = (Optional<?>) entry;
            if (optional.isEmpty()) targets.add(Optional.empty());
            else {
                var target = optional.get();
                var methods = TARGET_ACCESSORS.get(target.getClass());
                targets.add(Optional.of(new AbsoluteTarget((GlobalPos) methods.pos().invoke(target),
                        (Direction) methods.face().invoke(target))));
            }
        }
        return new Data(inputs, targets);
    }
}
