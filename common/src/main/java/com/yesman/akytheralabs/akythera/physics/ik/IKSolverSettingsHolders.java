package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.AkytheraIKSolverType;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// The editor holder each [IKSolverType] authors its settings through. Registering a type twice replaces it.
///
/// **Open registry**: external mods register their own solver types here during client init. Built-ins register from
/// this class's static initializer.
public final class IKSolverSettingsHolders {
    private IKSolverSettingsHolders() {}

    private static final Map<IKSolverType, Supplier<IKSolverSettingsHolder>> FACTORIES = new ConcurrentHashMap<>();

    public static void register(final IKSolverType type, final Supplier<IKSolverSettingsHolder> factory) {
        FACTORIES.put(type, factory);
    }

    /// A fresh settings holder for `type`, or null when nothing is registered for it.
    public static @Nullable IKSolverSettingsHolder create(final IKSolverType type) {
        Supplier<IKSolverSettingsHolder> factory = FACTORIES.get(type);

        return factory == null ? null : factory.get();
    }

    /// The types the solver combo offers, in the order the type manager assigned them.
    public static List<IKSolverType> types() {
        return IKSolverType.ENUM_MANAGER.universalValues().stream()
            .filter(FACTORIES::containsKey)
            .toList();
    }

    static {
        register(AkytheraIKSolverType.TWO_BONE, TwoBoneSolverHolder::new);
        register(AkytheraIKSolverType.FABRIK, FabrikSolverHolder::new);
    }
}
