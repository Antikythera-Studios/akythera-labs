package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.AkytheraBoneConstraintType;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraintType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// The editor holder each [BoneConstraintType] authors its settings through. Registering a type twice replaces it.
///
/// **Open registry**: external mods register their own constraint types here during client init. Built-ins register
/// from this class's static initializer.
public final class BoneConstraintSettingsHolders {
    private BoneConstraintSettingsHolders() {}

    private static final Map<BoneConstraintType, Supplier<BoneConstraintSettingsHolder>> FACTORIES = new ConcurrentHashMap<>();

    public static void register(final BoneConstraintType type, final Supplier<BoneConstraintSettingsHolder> factory) {
        FACTORIES.put(type, factory);
    }

    /// A fresh settings holder for `type`, or null when nothing is registered for it.
    public static @Nullable BoneConstraintSettingsHolder create(final BoneConstraintType type) {
        Supplier<BoneConstraintSettingsHolder> factory = FACTORIES.get(type);

        return factory == null ? null : factory.get();
    }

    /// The types the type combo offers, in the order the type manager assigned them.
    public static List<BoneConstraintType> types() {
        return BoneConstraintType.ENUM_MANAGER.universalValues().stream()
            .filter(FACTORIES::containsKey)
            .toList();
    }

    static {
        register(AkytheraBoneConstraintType.LIMIT_ROTATION, LimitRotationHolder::new);
        register(AkytheraBoneConstraintType.TRANSFORMATION, TransformationHolder::new);
    }
}
