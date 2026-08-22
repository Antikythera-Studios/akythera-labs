package com.yesman.akytheralabs.dataholder;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/// A temporary mutable data holder for registry objects that can be modified
/// while editing a project. This bridges the actual objects in registries and
/// GUI widgets that are interactive and modifiable.
///
/// Most implementations should extend [AbstractDataHolder], which carries the
/// holder's own [ResourceKey] (assigned by [ProjectObjectRegistry] on register / rekey).
public interface DataHolder<T> {
    /// The identifier of a not-yet-picked reference, `akythera:empty`, matching the engine's
    /// `AnimationManager.EMPTY_ANIMATION_ACCESSOR`. Holder defaults carry it rather than null, so the
    /// editor widgets can still route their pick modals off the value's runtime info — an accessor's
    /// `type()`, a ResourceKey's `registryKey()`, the declared generics being erased. It displays as
    /// `<none>`.
    Identifier EMPTY_REFERENCE = Akythera.identifier("empty");

    /// Overwrites this holder's state from `object`, discarding every unsaved change on it.
    void readFrom(final T object);

    /// Builds the runtime object from the authored state, in the shape the data pack [Codec] reads
    /// and syncs.
    T makeObject();

    /// The [EditorReference]s this holder currently references, none by default — the shared
    /// [ProjectObjectHolder] and editor `AssetAccessor` instances it holds. A rename walks these by
    /// identity and re-saves only the entries that point at the renamed target.
    default Stream<EditorReference> referencedReferences() {
        return Stream.empty();
    }

    /// Marks this holder as having failed to load.
    void markFailed(Exception exception);

    /// Retains the original serialized form of a holder whose deserialization failed, so a later
    /// save re-emits the untouched original instead of overwriting the file with defaults.
    /// Default: not stored (only [AbstractDataHolder] and the failed-element placeholders store it).
    default void retainRaw(JsonElement raw) {}

    /// The retained original form, or null when this holder loaded fine (or cannot store one).
    default @Nullable JsonElement retainedRaw() {
        return null;
    }

    /// Clears the failure and the retained raw — the holder's in-memory state becomes
    /// authoritative again. Wired to the replace-with-default popup on a failed content tile.
    default void markResolved() {}

    /// Failure of [#makeObject] at register time (a dangling reference, or the compile gate
    /// rejecting a graph with unresolved nodes). A **separate channel** from [#markFailed]: the
    /// holder's *data* decoded fine, so saving stays allowed and the replace-with-default popup
    /// must NOT trigger — only the tile signal and the export gate read this. Null clears it
    /// (a later successful [#makeObject]).
    default void markObjectFailed(@Nullable Exception exception) {}

    /// The register-time [#makeObject] failure, or null when the runtime object built fine.
    default @Nullable Exception getObjectCreationFailure() {
        return null;
    }

    /// The exception [AkytheraProject] hit loading this asset. Every holder field is unreliable
    /// while this is non-null.
    @Nullable Exception getDeserializationFailureException();

    /// The first nested-content deserialization failure this holder carries, or null when every
    /// nested piece is healthy. Distinct from [#getDeserializationFailureException]: the holder
    /// itself decoded into a structurally valid object but contains content the guards salvaged — a
    /// component reset to defaults, an unresolved graph node, a broken notify row.
    ///
    /// The entry is still invalid, so the content browser tile red-marks it and shows this in the
    /// tooltip, but it opens and edits normally with no replace popup. The mark clears once the
    /// healed content is flushed back onto the holder.
    default @Nullable Exception getNestedFailureException() {
        return null;
    }

    /// The first failure **this** entry carries, checked in severity order — the root decode failure
    /// ([#getDeserializationFailureException]), then a nested-content failure
    /// ([#getNestedFailureException]), then the runtime-object failure ([#getObjectCreationFailure])
    /// — or null when the entry itself is healthy (it may still depend on a broken one, see
    /// [#getFailureException]).
    default @Nullable Exception getOwnFailureException() {
        Exception decodeFailure = getDeserializationFailureException();
        if (decodeFailure != null) return decodeFailure;

        Exception nestedFailure = getNestedFailureException();
        if (nestedFailure != null) return nestedFailure;

        return getObjectCreationFailure();
    }

    /// The project entries this holder **depends on** — used to inherit their broken state (see
    /// [#getFailureException]). Derived from [#referencedReferences] by default, which covers every
    /// holder that references entries through their shared [ProjectObjectHolder]; a holder that
    /// references an entry by raw id instead (an Anim Graph's pose players) overrides this and adds
    /// those keys.
    ///
    /// Distinct from [#referencedReferences], which is about the shared *instances* a rename mutates
    /// in place: an id-only reference has no shared instance to re-save, but is still a dependency.
    default Stream<ResourceKey<?>> referencedEntries() {
        return referencedReferences()
            .filter(ProjectObjectHolder.class::isInstance)
            .map(ref -> (ProjectObjectHolder<?>) ref)
            .flatMap(holder -> holder.unwrapKey().stream());
    }

    /// This entry's own failure ([#getOwnFailureException]) or, when it is healthy itself, the failure
    /// of an entry it (transitively) references. This is the single definition of "broken": the
    /// content browser tile red-marks on it — so a blendspace with an unassigned node marks itself
    /// **and** every script playing it — and the export skips these entries (see
    /// [AkytheraProject#findBrokenEntries]), since emitting a referrer whose
    /// dependency was left out would ship a dangling reference.
    default @Nullable Exception getFailureException() {
        Exception own = getOwnFailureException();
        if (own != null) return own;

        return inheritedFailure(this, new HashSet<>());
    }

    /// Depth-first search for a broken entry among `holder`'s [#referencedEntries], reported by the
    /// broken entry's own id. `visited` carries the keys already walked, so a reference cycle
    /// (or a diamond) is traversed once. A key that resolves to no project holder — an engine / level
    /// entry, an unset reference, or one whose target was deleted — is not a failure here: the
    /// referrer's own validation owns that case (e.g. the Anim Graph's `No such asset` node error).
    private static @Nullable Exception inheritedFailure(DataHolder<?> holder, Set<ResourceKey<?>> visited) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return null;

        for (ResourceKey<?> key : holder.referencedEntries().toList()) {
            if (!visited.add(key)) continue;

            DataHolder<?> referenced = projectHolderOf(project, key);
            if (referenced == null) continue;

            Exception own = referenced.getOwnFailureException();
            if (own != null) {
                return new IllegalStateException(
                    "References broken entry " + key.identifier() + ": " + own.getMessage());
            }

            Exception inherited = inheritedFailure(referenced, visited);
            if (inherited != null) return inherited;
        }

        return null;
    }

    /// Captures `key`'s entry type so the project lookup stays generic-clean.
    private static <T> @Nullable DataHolder<T> projectHolderOf(AkytheraProject project, ResourceKey<T> key) {
        return project.<T, DataHolder<T>>getDataHolder(key).orElse(null);
    }
}