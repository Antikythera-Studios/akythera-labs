package com.yesman.akytheralabs.akythera.collider;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// The editor holder each [ColliderType] authors its dimensions through, and the codec that persists
/// it. Registering a type twice replaces it, which is how an addon restyles a built-in shape.
///
/// **Open registry**: external mods register their own shapes here during client init, alongside
/// their component holders. Built-ins register from this class's static initializer.
public final class ColliderShapeHolders {
    private ColliderShapeHolders() {}

    private record Entry(Supplier<ColliderShapeHolder> factory, MapCodec<? extends ColliderShapeHolder> codec) {}

    private static final Map<ColliderType, Entry> SHAPES = new ConcurrentHashMap<>();

    /// Editor persistence of a shape holder: its [ColliderType] under `shape`, dispatching to the
    /// codec registered for that type, the way [Collider#MAP_CODEC] dispatches the engine's shapes.
    ///
    /// @throws IllegalStateException on decode, for a shape nothing is registered for
    public static final MapCodec<ColliderShapeHolder> CODEC = ColliderType.ENUM_MANAGER.getNameCodec()
        .dispatchMap("shape", ColliderShapeHolder::type, type -> {
            Entry entry = SHAPES.get(type);

            if (entry == null) throw new IllegalStateException("No collider shape holder registered for " + type);

            return entry.codec();
        });

    public static void register(
        final ColliderType type,
        final Supplier<ColliderShapeHolder> factory,
        final MapCodec<? extends ColliderShapeHolder> codec
    ) {
        SHAPES.put(type, new Entry(factory, codec));
    }

    /// A fresh shape holder for `type`, or null when nothing is registered for it.
    public static @Nullable ColliderShapeHolder create(final ColliderType type) {
        Entry entry = SHAPES.get(type);

        return entry == null ? null : entry.factory().get();
    }

    /// The shapes the shape combo offers: the registered [ColliderType]s, in the order the type
    /// manager assigned them. A type with no holder here cannot be authored and is left out.
    public static List<ColliderType> shapes() {
        return ColliderType.ENUM_MANAGER.universalValues().stream()
            .filter(SHAPES::containsKey)
            .toList();
    }

    static {
        register(AkytheraColliderType.ORIENTED_BOX, OrientedBoxShapeHolder::new, OrientedBoxShapeHolder.EDITOR_CODEC);
        register(AkytheraColliderType.SPHERE, SphereShapeHolder::new, SphereShapeHolder.EDITOR_CODEC);
        register(AkytheraColliderType.CONE, ConeShapeHolder::new, ConeShapeHolder.EDITOR_CODEC);
        register(AkytheraColliderType.CAPSULE, CapsuleShapeHolder::new, CapsuleShapeHolder.EDITOR_CODEC);
    }
}
