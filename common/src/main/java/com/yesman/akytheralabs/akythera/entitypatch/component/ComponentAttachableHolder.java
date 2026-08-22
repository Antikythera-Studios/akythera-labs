package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import net.minecraft.resources.Identifier;

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
/// children, the standing example being [ArmatureComponentHolder] with its [BoneSocketHolder]s.
///
/// Which children may be **added** is declared by [#attachableTypes]; a root host lists
/// [RootAttachableComponent], which is what confines a bone socket to living under an armature.
public interface ComponentAttachableHolder {
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

    /// The persisted `components` field of one host — a name → component object map, so the engine's
    /// map key *is* the JSON key:
    ///
    /// ```json
    /// "components": {
    ///   "armature": {
    ///     "type": "akythera:armature", "armature": "epicfight:humanoid",
    ///     "components": { "right_hand": { "type": "akythera:bone_socket", ... } }
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
    static MapCodec<Optional<SequencedMap<String, ComponentDataHolder<?>>>> componentsField(
        Supplier<ComponentHolderRegistry> registry
    ) {
        return Codec.lazyInitialized(() -> {
            ComponentHolderRegistry resolved = registry.get();
            Codec<ComponentDataHolder<?>> component = AbstractDataHolder.guardedComponent(
                resolved.codec(), resolved::createDefault, FailedComponentHolder::new);

            return Codec.unboundedMap(Codec.STRING, component)
                .<SequencedMap<String, ComponentDataHolder<?>>>xmap(LinkedHashMap::new, Map::copyOf);
        }).optionalFieldOf("components");
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

    /// A default name for a freshly attached component of `typeId`: its path — `akythera:bone_socket`
    /// → `bone_socket` — de-duplicated against the host's existing children.
    static String defaultName(Identifier typeId, Set<String> taken) {
        return uniqueName(typeId.getPath(), taken);
    }

    /// One host's children lowered to their runtime blueprints, keyed by the same names — the shape
    /// [EntityPatchBlueprint] and [ItemPatchBlueprint] take.
    ///
    /// Covers this host's own level only. A component that hosts children lowers them itself, in whatever
    /// shape its runtime counterpart takes — an armature turns its sockets into a typed [BoneSocket] map.
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

    /// Every holder in the tree rooted at `host`, parents before their children.
    static Stream<ComponentDataHolder<?>> flatten(ComponentAttachableHolder host) {
        return host.getComponents().values().stream().flatMap(holder -> holder instanceof ComponentAttachableHolder child
            ? Stream.concat(Stream.of(holder), flatten(child))
            : Stream.of(holder));
    }
}