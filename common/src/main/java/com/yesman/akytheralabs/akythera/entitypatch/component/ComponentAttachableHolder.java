package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// Editor twin of [ComponentAttachable]: a holder that hosts other component holders, keyed by the
/// author-chosen name the engine looks them up under ([ComponentAttachable#getComponent]).
///
/// Hosts nest exactly like the engine's do — a child that is itself a `ComponentAttachableHolder`
/// hosts children of its own — so [#getComponents] is the component tree [ComponentEditorPanel]
/// lists. The blueprint holders implement it as the root host, as does any component that accepts
/// children, the standing example being [ArmatureComponentHolder] with its [TransformSocketHolder]s.
///
/// Which children may be **added** is declared by [#attachableTypes]; a root host lists
/// [RootAttachableComponent], which is what confines a bone socket to living under an armature.
public interface ComponentAttachableHolder {
    /// The JSON field [#componentsField] stores a host's children under.
    String CHILDREN_FIELD = "components";

    /// Holder classes that may be attached under this component. A component type is offered in
    /// this host's attach menu when its registered holder class
    /// ([ComponentHolderRegistry#holderClass]) is assignable to one of these — so an interface
    /// listed here admits every holder implementing it. An empty list means "nothing may be
    /// attached", and the attach menu is not offered at all.
    List<Class<?>> attachableTypes();

    /// The attached children by name, in the order the editor lists and the file stores them in.
    /// Mutable and live: the panel adds, removes, and re-keys entries in place.
    SequencedMap<String, ComponentDataHolder<?>> getComponents();

    /// Whether `holderClass` passes [#attachableTypes].
    default boolean accepts(Class<?> holderClass) {
        return attachableTypes().stream().anyMatch(type -> type.isAssignableFrom(holderClass));
    }

    /// An empty, insertion-ordered map for a host to back [#getComponents] with.
    static SequencedMap<String, ComponentDataHolder<?>> newComponentMap() {
        return new LinkedHashMap<>();
    }

    /// `hosts`' components folded root-first into one map, the nearest declaration of a merge
    /// identity winning. The inherited-component listing an editor shows above a patch's own.
    ///
    /// The holder-side counterpart of [PatchComponent#fold], deliberately apart from it: this side
    /// lists what is inherited and displays an overridden component from the local holder, so it has
    /// no [PatchComponent#overwrite] step and never builds a component neither host declared.
    static SequencedMap<String, ComponentDataHolder<?>> foldChain(List<? extends ComponentAttachableHolder> hosts) {
        SequencedMap<String, ComponentDataHolder<?>> folded = newComponentMap();
        Map<Object, String> slotsByMergeKey = new HashMap<>();

        for (ComponentAttachableHolder host : hosts) {
            host.getComponents().forEach((name, holder) -> {
                Object mergeKey = holder.mergeKey();
                String slot = mergeKey == null ? name : slotsByMergeKey.computeIfAbsent(mergeKey, _ -> name);
                folded.put(slot, holder);
            });
        }

        return folded;
    }

    /// The component of `components` a local component of `holder`'s merge identity would take the
    /// place of, or null when nothing there matches it.
    static @Nullable ComponentDataHolder<?> matching(
        Map<String, ComponentDataHolder<?>> components, String name, ComponentDataHolder<?> holder
    ) {
        Object mergeKey = holder.mergeKey();

        if (mergeKey == null) return components.get(name);

        return components.values().stream()
            .filter(candidate -> mergeKey.equals(candidate.mergeKey()))
            .findFirst()
            .orElse(null);
    }

    /// The persisted `components` field of one host — a name → component object map, so the engine's
    /// map key *is* the JSON key:
    ///
    /// ```json
    /// "components": {
    ///   "armature": {
    ///     "type": "akythera:armature", "armature": "epicfight:humanoid",
    ///     "components": { "right_hand": { "type": "akythera:transform_socket", ... } }
    ///   }
    /// }
    /// ```
    ///
    /// Nesting comes for free: a host's own codec declares this field, and the dispatch inside
    /// resolves back to the registry that host's codec is itself registered in. `registry` is a
    /// supplier and the element codec is built under [Codec#lazyInitialized], because a holder's
    /// `EDITOR_CODEC` and its registration entry initialize each other — eagerly, whichever class
    /// loaded second would hand the other a null.
    ///
    /// The field decodes through an [Optional]. A codec's default value is a single instance shared
    /// by every decode that falls back to it, and for a mutable map that would wire every childless
    /// host in the project together. Pair with [#componentsOf] / [#applyComponents], which keep the
    /// field out of the JSON entirely when a host has no children.
    ///
    /// [Codec#unboundedMap] keeps insertion order in both directions and rejects a duplicate key
    /// outright, rather than collapsing two same-named components into one. Each component is
    /// wrapped in [AbstractDataHolder#guardedComponent], so a malformed one neither fails the file
    /// nor takes its children with it: a known type opens with default values, marked failed and
    /// healed on the next save; an unknown type becomes a [FailedComponentHolder] round-tripping its
    /// raw JSON.
    ///
    /// Both `xmap` ends copy through a [LinkedHashMap], so the order the editor lists a host's
    /// children in is the order the file stores them in.
    static MapCodec<Optional<SequencedMap<String, ComponentDataHolder<?>>>> componentsField(
        Supplier<ComponentHolderRegistry> registry
    ) {
        return Codec.lazyInitialized(() -> {
            ComponentHolderRegistry resolved = registry.get();
            Codec<ComponentDataHolder<?>> component = AbstractDataHolder.guardedComponent(
                resolved.codec(), id -> resolved.createDefault(id, null), FailedComponentHolder::new);

            return Codec.unboundedMap(Codec.STRING, component)
                .<SequencedMap<String, ComponentDataHolder<?>>>xmap(LinkedHashMap::new, LinkedHashMap::new);
        }).optionalFieldOf(CHILDREN_FIELD);
    }

    /// Encode side of [#componentsField]: a host's children, or empty when it has none so the
    /// field is omitted rather than written as `{}`.
    static Optional<SequencedMap<String, ComponentDataHolder<?>>> componentsOf(ComponentAttachableHolder host) {
        return host.getComponents().isEmpty() ? Optional.empty() : Optional.of(host.getComponents());
    }

    /// Decode side of [#componentsField]: copies the decoded children into `host`'s own map, so the
    /// host never aliases whatever instance the codec produced.
    static void applyComponents(
        ComponentAttachableHolder host,
        Optional<SequencedMap<String, ComponentDataHolder<?>>> decoded
    ) {
        decoded.ifPresent(host.getComponents()::putAll);
    }

    /// `name` if it is free among `taken`, otherwise the first `name_1`, `name_2`, … that is.
    static String uniqueName(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;

        for (int i = 1; ; i++) {
            String candidate = name + "_" + i;
            if (!taken.contains(candidate)) return candidate;
        }
    }

    /// A default name for a freshly attached component of `typeId`: its path — `akythera:transform_socket`
    /// → `transform_socket` — de-duplicated against the host's existing children.
    static String defaultName(Identifier typeId, Set<String> taken) {
        return uniqueName(typeId.getPath(), taken);
    }

    /// Inverse of [#lowerComponents] for one host level: rebuilds fresh editor holders from runtime
    /// blueprints, keyed the same, against `registry`. A blueprint whose type has no registered holder
    /// is skipped — the editor has nothing to show it in.
    ///
    /// Replaces the host's current children.
    @SuppressWarnings("unchecked")
    static void readComponents(
        ComponentAttachableHolder host,
        ComponentHolderRegistry registry,
        Map<String, ? extends ComponentBlueprint<?, ?>> blueprints
    ) {
        host.getComponents().clear();

        blueprints.forEach((name, blueprint) -> {
            Identifier typeId = registry.blueprintTypeId(blueprint);
            if (typeId == null) return;

            ComponentDataHolder<?> holder = registry.createDefault(typeId, null);
            if (holder == null) return;

            // The runtime component records are their own blueprints, so a holder's readFrom takes one
            // directly. The cast recovers what the wildcard erased.
            ((ComponentDataHolder<ComponentBlueprint<?, ?>>)holder).readFrom(blueprint);
            holder.setEnabled(blueprint.enabled());
            host.getComponents().put(name, holder);
        });
    }

    /// One host's children lowered to their runtime blueprints, keyed by the same names — the shape
    /// [EntityPatchBlueprint] and [ItemPatchBlueprint] take.
    ///
    /// Covers this host's own level only. A component that hosts children lowers them itself, calling
    /// this for its own level.
    ///
    /// Skips [FailedComponentHolder]s, the only components with no runtime object. A defaults-fallback
    /// component — known type, marked failed — is a real component and lowers.
    static Map<String, ComponentBlueprint<?, ?>> lowerComponents(Map<String, ComponentDataHolder<?>> components) {
        Map<String, ComponentBlueprint<?, ?>> lowered = new LinkedHashMap<>();

        components.forEach((name, holder) -> {
            if (holder instanceof FailedComponentHolder) return;
            lowered.put(name, holder.makeObject());
        });

        return lowered;
    }

    /// [#flatten] without the switched-off holders and everything under them — the components a patch
    /// built from this tree takes part with, see [PatchComponent#isEnabled].
    static Stream<ComponentDataHolder<?>> flattenEnabled(ComponentAttachableHolder host) {
        return host.getComponents().values().stream()
            .filter(ComponentDataHolder::isEnabled)
            .flatMap(holder -> holder instanceof ComponentAttachableHolder child
                ? Stream.concat(Stream.of(holder), flattenEnabled(child))
                : Stream.of(holder));
    }

    /// Every holder in the tree rooted at `host`, parents before their children.
    static Stream<ComponentDataHolder<?>> flatten(ComponentAttachableHolder host) {
        return host.getComponents().values().stream().flatMap(holder -> holder instanceof ComponentAttachableHolder child
            ? Stream.concat(Stream.of(holder), flatten(child))
            : Stream.of(holder));
    }

    /// The holder `component` is attached under in the tree rooted at `root`, or null when it hangs off `root` itself or
    /// nowhere in it.
    static @Nullable ComponentAttachableHolder hostOf(ComponentAttachableHolder root, ComponentDataHolder<?> component) {
        return flatten(root)
            .filter(ComponentAttachableHolder.class::isInstance)
            .map(ComponentAttachableHolder.class::cast)
            .filter(host -> host.getComponents().containsValue(component))
            .findFirst()
            .orElse(null);
    }
}