package com.yesman.akytheralabs.dataholder.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/// Maps entity-patch component **type ids** (the
/// [com.yesman.akythera.core.registry.AkytheraRegistries.Frozen#COMPONENT_BLUEPRINT_TYPES] registry
/// ids) to their editor-side holders: a default factory for freshly added components and the editor
/// persistence codec. A component type without an entry here shows up in the Components list but has
/// no Details editor (and is dropped on save), until its holder is registered.
public final class EntityPatchComponentHolders {
    private EntityPatchComponentHolders() {}

    public static final Identifier ARMATURE = Akythera.identifier("armature");

    private record Entry(
        Supplier<? extends AbstractDataHolder<?>> defaultFactory,
        MapCodec<? extends AbstractDataHolder<?>> codec
    ) {}

    private static final Map<Identifier, Entry> TYPES = Map.of(
        ARMATURE, new Entry(ArmatureComponentHolder::createDefault, ArmatureComponentHolder.EDITOR_CODEC)
    );

    /// Editor persistence codec for one component holder: the component type id rides in a `type`
    /// field and dispatches to that type's holder codec. Unknown ids fail the decode with a proper
    /// error instead of throwing.
    public static final Codec<AbstractDataHolder<?>> CODEC = Identifier.CODEC.partialDispatch(
        "type",
        holder -> DataResult.success(typeIdOf(holder)),
        id -> Optional.ofNullable(TYPES.get(id))
            .<DataResult<MapCodec<? extends AbstractDataHolder<?>>>>map(entry -> DataResult.success(entry.codec()))
            .orElseGet(() -> DataResult.error(() -> "No editor holder registered for component type " + id))
    );

    /// The component type id a holder instance belongs to. Every registered holder class must have
    /// a case here — it is the encode side of [#CODEC]'s dispatch.
    public static Identifier typeIdOf(AbstractDataHolder<?> holder) {
        return switch (holder) {
            case ArmatureComponentHolder _ -> ARMATURE;
            default -> throw new IllegalArgumentException(
                "No component type id registered for holder " + holder.getClass().getName());
        };
    }

    /// A default holder for a freshly added component of `typeId`, or null when the type has no
    /// editor support yet.
    public static @Nullable AbstractDataHolder<?> createDefault(Identifier typeId) {
        Entry entry = TYPES.get(typeId);
        return entry == null ? null : entry.defaultFactory().get();
    }
}
