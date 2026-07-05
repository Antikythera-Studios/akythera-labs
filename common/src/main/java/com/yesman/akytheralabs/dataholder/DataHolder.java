package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.Identifier;

import java.util.stream.Stream;

/// A temporary mutable data holder for registry objects that can be modified
/// while editing a project. This bridges the actual objects in registries and
/// GUI widgets that are interactive and modifiable.
///
/// Most implementations should extend [AbstractDataHolder], which carries the
/// holder's own [ResourceKey] (assigned by [ProjectObjectRegistry] on register / rekey).
public interface DataHolder<T> {
    /// The identifier of a **not-yet-picked** reference (`akythera:empty`, matching the engine's
    /// `AnimationManager.EMPTY_ANIMATION_ACCESSOR`). Holder defaults use it instead of null because
    /// the editor widgets route their pick modals off the value's runtime info (an accessor's
    /// `type()`, a ResourceKey's `registryKey()` — the declared generics are erased). The editor
    /// displays it as `<none>`.
    Identifier EMPTY_REFERENCE = Akythera.identifier("empty");
    /// Changes the data holder state according to given object. This will blow
    /// up all unsaved changes so proper warning handling is essential.
    void readFrom(final T object);

    /// Based on the user created data in UI stage, it builds a modified data
    /// to be used in exporting by [Codec] used by reading & syncing data in
    /// data pack.
    T makeObject();

    /// The [EditorReference]s this holder currently references (default: none) — the shared
    /// [ProjectObjectHolder] / editor `AssetAccessor` instances it holds. Used at rename time to
    /// re-save only the entries that actually point at a renamed target (matched by identity): the
    /// forward, on-demand replacement for a maintained reverse reference index.
    default Stream<EditorReference> referencedReferences() {
        return Stream.empty();
    }
}