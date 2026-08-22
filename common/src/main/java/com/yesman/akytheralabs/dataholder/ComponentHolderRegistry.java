package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Maps patch component **type ids** (the [AkytheraRegistries.Frozen#COMPONENT_BLUEPRINT_TYPES] registry ids)
/// to their editor-side holders. One instance per patch kind — see [EntityPatchComponentHolders] and
/// [ItemPatchComponentHolders].
///
/// **Open registry**: external mods (compat plugins) register their own component holders through
/// [#register] during client init — before the first project entry decodes and before the first
/// editor screen opens. Built-ins register from their owning registry class's static initializer.
///
/// A component type without an entry here shows up in neither the add nor the attach menu, and a
/// stored one decodes to a [FailedComponentHolder] that round-trips its raw JSON untouched.
public final class ComponentHolderRegistry {
    private record Entry(
        Class<? extends ComponentDataHolder<?>> holderClass,
        Supplier<? extends ComponentDataHolder<?>> defaultFactory,
        MapCodec<? extends ComponentDataHolder<?>> codec
    ) {}

    private final Map<Identifier, Entry> types = new ConcurrentHashMap<>();

    /// Encode side of [#codec]'s dispatch — the registered holder class's component type id.
    private final Map<Class<?>, Identifier> typeIdsByClass = new ConcurrentHashMap<>();

    /// [#renderStateOf]'s answers, by component type id. An empty [Optional] marks a type that
    /// declares no render state.
    private final Map<Identifier, Optional<Class<? extends EntityRenderState>>> renderStates = new ConcurrentHashMap<>();

    /// Editor persistence codec for one component holder: the component type id rides in a `type`
    /// field and dispatches to that type's holder codec. Unknown ids fail the decode with a proper
    /// error instead of throwing — the guarded wrapper turns that into a [FailedComponentHolder].
    private final Codec<ComponentDataHolder<?>> codec = Identifier.CODEC.partialDispatch(
        "type",
        holder -> DataResult.success(typeIdOf(holder)),
        id -> Optional.ofNullable(types.get(id))
            .<DataResult<MapCodec<? extends ComponentDataHolder<?>>>>map(entry -> DataResult.success(entry.codec()))
            .orElseGet(() -> DataResult.error(() -> "No editor holder registered for component type " + id))
    );

    /// Registers the editor holder for the component type `typeId`: the concrete holder class (one
    /// component type per class — it keys the encode dispatch and the suggestion filters), a default
    /// factory for freshly added components, and the editor persistence codec.
    public <H extends ComponentDataHolder<?>> void register(
        Identifier typeId,
        Class<H> holderClass,
        Supplier<? extends H> defaultFactory,
        MapCodec<? extends H> codec
    ) {
        if (types.putIfAbsent(typeId, new Entry(holderClass, defaultFactory, codec)) != null) {
            throw new IllegalStateException("Component holder already registered for " + typeId);
        }

        typeIdsByClass.put(holderClass, typeId);
    }

    public Codec<ComponentDataHolder<?>> codec() {
        return codec;
    }

    /// The component type id a holder instance belongs to — resolved through the class map filled
    /// by [#register].
    public Identifier typeIdOf(ComponentDataHolder<?> holder) {
        Identifier id = typeIdOrNull(holder);

        if (id == null) {
            throw new IllegalArgumentException(
                "No component type id registered for holder " + holder.getClass().getName());
        }

        return id;
    }

    /// [#typeIdOf] for callers that legitimately meet an unregistered holder — a
    /// [FailedComponentHolder] stands in for a type this build knows nothing about.
    public @Nullable Identifier typeIdOrNull(ComponentDataHolder<?> holder) {
        return typeIdsByClass.get(holder.getClass());
    }

    /// A default holder for a freshly added component of `typeId`, or null when the type has no
    /// editor support yet.
    public @Nullable ComponentDataHolder<?> createDefault(Identifier typeId) {
        Entry entry = types.get(typeId);
        return entry == null ? null : entry.defaultFactory().get();
    }

    /// The registered holder class of `typeId`, or null when the type has no editor support. The
    /// component panel's suggestion filters test it — [RootAttachableComponent] for the root add
    /// menu, [ComponentAttachableHolder#attachableTypes] for a row's attach menu.
    public @Nullable Class<? extends ComponentDataHolder<?>> holderClass(Identifier typeId) {
        Entry entry = types.get(typeId);
        return entry == null ? null : entry.holderClass();
    }

    /// Whether `typeId`'s holder may be attached directly on a patch (as opposed to only under
    /// another component), i.e. whether it implements [RootAttachableComponent].
    public boolean isRootAttachable(Identifier typeId) {
        Class<?> holderClass = holderClass(typeId);
        return holderClass != null && RootAttachableComponent.class.isAssignableFrom(holderClass);
    }

    /// The [EntityRenderState] `typeId`'s component draws through, per
    /// [RenderableComponent#stateType], or null when it declares none: the type is not renderable, or
    /// its blueprint is not itself the component and so has no instance to ask.
    ///
    /// Builds one default holder per type and keeps the answer. A holder whose defaults are too
    /// incomplete to lower counts as declaring none.
    public @Nullable Class<? extends EntityRenderState> renderStateOf(Identifier typeId) {
        return renderStates.computeIfAbsent(typeId, id -> {
            ComponentDataHolder<?> holder = createDefault(id);

            if (holder == null) return Optional.empty();

            try {
                return holder.makeObject() instanceof RenderableComponent<?> renderable
                    ? Optional.of(renderable.stateType())
                    : Optional.empty();
            } catch (RuntimeException _) {
                return Optional.empty();
            }
        }).orElse(null);
    }
}