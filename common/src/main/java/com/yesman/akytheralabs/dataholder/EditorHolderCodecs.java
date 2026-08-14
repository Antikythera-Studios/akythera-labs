package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.stream.Stream;

/// `Identifier` &harr; live [Holder] codecs for referencing a **data pack registry entry** by id while
/// keeping the shared instance in memory, so a rename of the target propagates without rewriting the
/// referrer (see [ProjectObjectRegistry#rekey], [ProjectObjectRegistry#remapNamespace]).
///
/// An id that resolves in neither the project nor the level decodes to the project registry's
/// dangling holder rather than failing: the referring file loads, the reference round-trips through
/// its key on save, and the editor marks it red. The counterpart for editable data blocks
/// (`FSMAnimationState`, `AnimationLayerSignature`, `AnimationInterfaceKey`) is
/// [DataBlockHolder#holderCodec].
///
/// Editor-only — the referencing records are never serialized at engine runtime, so
/// `getCurrentProject()` is always valid here.
public final class EditorHolderCodecs {
    private EditorHolderCodecs() {}

    public static <T> Codec<Holder<T>> entry(ResourceKey<? extends Registry<T>> registryKey) {
        return Identifier.CODEC.xmap(id -> resolve(registryKey, id), EditorHolderCodecs::entryId);
    }

    /// The shared live holder for `id` in `registryKey` — project &cup; level, falling back to the
    /// project registry's dangling placeholder so an unresolved id still round-trips.
    public static <T> Holder<T> resolve(ResourceKey<? extends Registry<T>> registryKey, Identifier id) {
        return AkytheraProject.getCurrentProject().getEntryHolderOrDangling(ResourceKey.create(registryKey, id));
    }

    /// A registry-entry holder's current id. Every holder on this path is key-backed (a project
    /// holder, a level holder, or a dangling placeholder), so the key is always present.
    public static Identifier entryId(Holder<?> holder) {
        return holder.unwrapKey().orElseThrow().identifier();
    }

    /// A data-block holder's current id: its key when the block is editor-created (a
    /// [ProjectObjectHolder], which follows renames), else the engine value's own id — a hardcoded
    /// block resolves to a keyless [Holder#direct].
    public static Identifier blockId(Holder<? extends EnumerableDataBlock> holder) {
        return holder.unwrapKey()
            .map(ResourceKey::identifier)
            .orElseGet(() -> holder.value().identifier());
    }

    /// The [EditorReference] behind `holder`, or nothing when the target cannot rename — a level or
    /// installed-mod entry, and any engine data block (both are plain registry holders).
    public static Stream<EditorReference> referenceOf(Holder<?> holder) {
        return holder instanceof EditorReference ref ? Stream.of(ref) : Stream.empty();
    }

    /// `holder`'s id when it currently resolves to nothing, for the dangling-reference gate.
    public static Stream<Identifier> missingOf(Holder<?> holder) {
        if (holder.isBound()) return Stream.empty();

        return holder.unwrapKey().map(ResourceKey::identifier).stream();
    }
}
