package com.yesman.akytheralabs.akythera.collider;

import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// The editor holder each [ColliderType] authors its dimensions through. Registering a type twice
/// replaces it, which is how an addon restyles a built-in shape.
///
/// **Open registry**: external mods register their own shapes here during client init, alongside
/// their component holders. Built-ins register from this class's static initializer.
public final class ColliderShapeHolders {
    private ColliderShapeHolders() {}

    private static final Map<ColliderType, Supplier<ColliderShapeHolder>> FACTORIES = new ConcurrentHashMap<>();

    public static void register(final ColliderType type, final Supplier<ColliderShapeHolder> factory) {
        FACTORIES.put(type, factory);
    }

    /// A fresh shape holder for `type`, or null when nothing is registered for it.
    public static @Nullable ColliderShapeHolder create(final ColliderType type) {
        Supplier<ColliderShapeHolder> factory = FACTORIES.get(type);

        return factory == null ? null : factory.get();
    }

    /// The shapes the shape combo offers: the registered [ColliderType]s, in the order the type
    /// manager assigned them. A type with no holder here cannot be authored and is left out.
    public static List<ColliderType> shapes() {
        return ColliderType.ENUM_MANAGER.universalValues().stream()
            .filter(FACTORIES::containsKey)
            .toList();
    }

    static {
        register(AkytheraColliderType.ORIENTED_BOX, OrientedBoxShapeHolder::new);
        register(AkytheraColliderType.SPHERE, SphereShapeHolder::new);
        register(AkytheraColliderType.CONE, ConeShapeHolder::new);
        register(AkytheraColliderType.CAPSULE, CapsuleShapeHolder::new);
    }
}
