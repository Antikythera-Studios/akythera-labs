package com.yesman.akytheralabs.dataholder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/// Maps patch component **type ids** to their editor-side holders. One instance per patch kind, each
/// mirroring the engine component registry of that kind — see [EntityPatchComponentHolders] and
/// [ItemPatchComponentHolders].
///
/// **Open registry**: external mods (compat plugins) register their own component holders through
/// [#register] during client init — before the first project entry decodes and before the first
/// editor screen opens. Built-ins register from their owning registry class's static initializer.
///
/// A component type without an entry here shows up in neither the add nor the attach menu, and a
/// stored one decodes to a [FailedComponentHolder] that round-trips its raw JSON untouched.
public final class ComponentHolderRegistry {
    /// Why one more component of a type may not be attached under a host, or null when it may.
    /// Declared per type at [ComponentHolderRegistry#register] time, which is where a component type's
    /// own attachment rules live.
    @FunctionalInterface
    public interface AttachRule {
        /// @param root the blueprint holder the tree `host` sits in hangs off, `host` itself at the top level
        @Nullable Component refusal(ComponentAttachableHolder host, ComponentAttachableHolder root);
    }

    /// Builds a freshly added component of one type. `host` is what the component is about to be
    /// attached under, for a type whose defaults depend on its surroundings; null where there is no
    /// such context — the throwaway builds [ComponentHolderRegistry#componentTypeOf] and
    /// [ComponentHolderRegistry#renderStateOf] make to read a type's traits off, and the decode paths,
    /// whose defaults are overwritten by the stored values right after.
    @FunctionalInterface
    public interface DefaultFactory<H extends ComponentDataHolder<?>> {
        H create(@Nullable ComponentAttachableHolder host);

        /// [#create] for a component attached from a menu, where `root` is the blueprint holder the tree `host` sits
        /// in hangs off, for a type whose defaults depend on the whole tree.
        default H create(ComponentAttachableHolder host, ComponentAttachableHolder root) {
            return create(host);
        }
    }

    /// How a registry's attach menu lists what it offers: one submenu per category, holding the types and the
    /// ready-made components of that category.
    public interface AttachMenu {
        /// The categories, in the order the menu lists them.
        List<Component> categories();

        /// The one of [#categories] `typeId` is listed under, or null to leave it out of the menu.
        @Nullable Component categoryOf(Identifier typeId);

        /// The ready-made components offered in the tree hanging off `root`.
        default List<Preset> presets(final ComponentAttachableHolder root) {
            return List.of();
        }
    }

    /// A ready-made component the attach menu offers beside the bare types: listed as `label` under `category`
    /// and the title row of the mod `modId`, attaching what `factory` builds, a component of `typeId`, under `name`.
    public record Preset(
        Component category, String modId, Component label, Identifier typeId, String name, Supplier<? extends ComponentDataHolder<?>> factory
    ) {
    }

    /// Why a component of `typeId` may not be attached under `host`, or null when it may. Unlike an [AttachRule],
    /// one rule covers every type of the registry, those registered later included, see [#setCommonRule].
    @FunctionalInterface
    public interface TypeAttachRule {
        /// @param root the blueprint holder the tree `host` sits in hangs off, `host` itself at the top level
        @Nullable Component refusal(Identifier typeId, ComponentAttachableHolder host, ComponentAttachableHolder root);
    }

    private record Entry(
        Class<? extends ComponentDataHolder<?>> holderClass,
        DefaultFactory<?> defaultFactory,
        MapCodec<? extends ComponentDataHolder<?>> codec,
        AttachRule attachRule
    ) {}

    private final Map<Identifier, Entry> types = new ConcurrentHashMap<>();

    /// Former type ids, read only, mapped to the type id they decode as.
    private final Map<Identifier, Identifier> aliases = new ConcurrentHashMap<>();

    /// How the attach menu groups the types, see [AttachMenu]. Null lists the types alone, ungrouped.
    @Nullable
    private AttachMenu attachMenu;

    /// Asked for every type before its own [AttachRule]. Null refuses nothing.
    @Nullable
    private TypeAttachRule commonRule;

    /// The engine registry this one mirrors: [AkytheraRegistries.Frozen#ENTITY_PATCH_COMPONENT_TYPES]
    /// or [AkytheraRegistries.Frozen#ITEM_PATCH_COMPONENT_TYPES].
    private final Registry<MapCodec<? extends ComponentBlueprint<?, ?>>> blueprintTypes;

    /// Encode side of [#codec]'s dispatch — the registered holder class's component type id.
    private final Map<Class<?>, Identifier> typeIdsByClass = new ConcurrentHashMap<>();

    /// [#renderStateOf]'s answers, by component type id. An empty [Optional] marks a type that
    /// declares no render state.
    private final Map<Identifier, Optional<Class<? extends EntityRenderState>>> renderStates = new ConcurrentHashMap<>();

    /// [#componentTypeOf]'s answers, by component type id.
    private final Map<Identifier, Optional<Class<? extends PatchComponent>>> componentTypes = new ConcurrentHashMap<>();

    /// Editor persistence codec for one component holder: the component type id rides in a `type`
    /// field and dispatches to that type's holder codec. Unknown ids fail the decode with a proper
    /// error instead of throwing — the guarded wrapper turns that into a [FailedComponentHolder].
    private final Codec<ComponentDataHolder<?>> codec = Identifier.CODEC.partialDispatch(
        "type",
        holder -> DataResult.success(typeIdOf(holder)),
        id -> Optional.ofNullable(types.get(aliases.getOrDefault(id, id)))
            .<DataResult<MapCodec<? extends ComponentDataHolder<?>>>>map(entry -> DataResult.success(entry.codec()))
            .orElseGet(() -> DataResult.error(() -> "No editor holder registered for component type " + id))
    );

    public ComponentHolderRegistry(Registry<MapCodec<? extends ComponentBlueprint<?, ?>>> blueprintTypes) {
        this.blueprintTypes = blueprintTypes;
    }

    /// Every component type this patch kind knows, holder or not.
    public Registry<MapCodec<? extends ComponentBlueprint<?, ?>>> blueprintTypes() {
        return blueprintTypes;
    }

    /// The type id `blueprint` is registered under, or null when this patch kind's registry does not
    /// carry it.
    public @Nullable Identifier blueprintTypeId(ComponentBlueprint<?, ?> blueprint) {
        return blueprintTypes.getKey(blueprint.mapCodec());
    }

    /// Registers the editor holder for the component type `typeId`: the concrete holder class (one
    /// component type per class — it keys the encode dispatch and the suggestion filters), a default
    /// factory for freshly added components, and the editor persistence codec.
    ///
    /// The codec is stored with [ComponentBlueprint#ENABLED_CODEC] alongside it, so every type
    /// persists [ComponentDataHolder#isEnabled] without declaring it.
    public <H extends ComponentDataHolder<?>> void register(
        Identifier typeId,
        Class<H> holderClass,
        DefaultFactory<? extends H> defaultFactory,
        MapCodec<H> codec
    ) {
        register(typeId, holderClass, defaultFactory, codec, (_, _) -> null);
    }

    /// [#register] for a type that refuses some hosts — see [AttachRule].
    public <H extends ComponentDataHolder<?>> void register(
        Identifier typeId,
        Class<H> holderClass,
        DefaultFactory<? extends H> defaultFactory,
        MapCodec<H> codec,
        AttachRule attachRule
    ) {
        MapCodec<H> switched = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                codec.forGetter(Function.identity()),
                ComponentBlueprint.ENABLED_CODEC.forGetter(ComponentDataHolder::isEnabled)
            ).apply(instance, (holder, enabled) -> {
                holder.setEnabled(enabled);
                return holder;
            })
        );

        if (types.putIfAbsent(typeId, new Entry(holderClass, defaultFactory, switched, attachRule)) != null) {
            throw new IllegalStateException("Component holder already registered for " + typeId);
        }

        typeIdsByClass.put(holderClass, typeId);
    }

    /// Reads a holder stored under the former id `alias` as one of `typeId`. It is written back under `typeId`.
    public void registerAlias(Identifier alias, Identifier typeId) {
        aliases.put(alias, typeId);
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
    /// editor support yet. `host` is the component's prospective host, see [DefaultFactory].
    public @Nullable ComponentDataHolder<?> createDefault(
        Identifier typeId, @Nullable ComponentAttachableHolder host
    ) {
        Entry entry = types.get(typeId);
        return entry == null ? null : entry.defaultFactory().create(host);
    }

    /// [#createDefault] for a component about to be attached under `host` in the tree hanging off `root`, see
    /// [DefaultFactory#create(ComponentAttachableHolder, ComponentAttachableHolder)].
    public @Nullable ComponentDataHolder<?> createDefault(Identifier typeId, ComponentAttachableHolder host, ComponentAttachableHolder root) {
        Entry entry = types.get(typeId);
        return entry == null ? null : entry.defaultFactory().create(host, root);
    }

    /// `holder` encoded without its children, so two hosts compare by their own declaration. Null for
    /// a holder no type is registered for — a [FailedComponentHolder], which declares nothing this
    /// side understands.
    private @Nullable JsonElement declarationOf(ComponentDataHolder<?> holder) {
        if (typeIdOrNull(holder) == null) return null;

        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, holder).result().orElse(null);

        if (json instanceof JsonObject object) object.remove(ComponentAttachableHolder.CHILDREN_FIELD);

        return json;
    }

    /// A detached copy of `holder`'s declaration, its children left behind. What a patch takes over
    /// when it starts overriding an inherited component. Null when the round trip finds nothing to
    /// copy.
    public @Nullable ComponentDataHolder<?> copyOf(ComponentDataHolder<?> holder) {
        JsonElement declaration = declarationOf(holder);

        return declaration == null ? null : codec.parse(JsonOps.INSTANCE, declaration).result().orElse(null);
    }

    /// Whether the two declare the same thing — what greys a component row, and what a revert drops an
    /// override on once it holds again.
    public boolean sameDeclaration(ComponentDataHolder<?> a, ComponentDataHolder<?> b) {
        JsonElement left = declarationOf(a);

        return left != null && Objects.equals(left, declarationOf(b));
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

    /// The runtime [PatchComponent] class `typeId`'s component eventually becomes
    /// ([ComponentDataHolder#generateEventually]), or null when the type has no editor support.
    ///
    /// Builds one default holder per type and keeps the answer.
    public @Nullable Class<? extends PatchComponent> componentTypeOf(Identifier typeId) {
        return componentTypes.computeIfAbsent(typeId, id -> {
            ComponentDataHolder<?> holder = createDefault(id, null);
            return holder == null ? Optional.empty() : Optional.of(holder.generateEventually());
        }).orElse(null);
    }

    public @Nullable AttachMenu attachMenu() {
        return attachMenu;
    }

    public void setAttachMenu(final @Nullable AttachMenu attachMenu) {
        this.attachMenu = attachMenu;
    }

    /// Sets [#commonRule].
    public void setCommonRule(final @Nullable TypeAttachRule commonRule) {
        this.commonRule = commonRule;
    }

    /// Why `typeId` may not be attached under `host` in the tree hanging off `root`, or null when it may: a
    /// [UniqueComponent] the host already carries, else the type's own [AttachRule].
    public @Nullable Component attachRefusal(Identifier typeId, ComponentAttachableHolder host, ComponentAttachableHolder root) {
        Entry entry = types.get(typeId);
        if (entry == null) return null;

        Class<? extends PatchComponent> componentType = componentTypeOf(typeId);

        if (componentType != null
            && UniqueComponent.class.isAssignableFrom(componentType)
            && host.getComponents().values().stream().anyMatch(h -> h.generateEventually() == componentType)) {
            return Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REFUSED_UNIQUE);
        }

        Component common = commonRule == null ? null : commonRule.refusal(typeId, host, root);

        return common != null ? common : entry.attachRule().refusal(host, root);
    }

    /// The [EntityRenderState] `typeId`'s component draws through, per
    /// [RenderableComponent#stateType], or null when it declares none: the type is not renderable, or
    /// its blueprint is not itself the component and so has no instance to ask.
    ///
    /// Builds one default holder per type and keeps the answer. A holder whose defaults are too
    /// incomplete to lower counts as declaring none.
    public @Nullable Class<? extends EntityRenderState> renderStateOf(Identifier typeId) {
        return renderStates.computeIfAbsent(typeId, id -> {
            ComponentDataHolder<?> holder = createDefault(id, null);

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