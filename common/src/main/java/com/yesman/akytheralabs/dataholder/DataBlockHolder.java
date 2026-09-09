package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;

/// Editor-side holder for one editable [EnumerableDataBlock] type (those registered in
/// `EnumerableDataBlockSync#LISTENERS` — currently `FSMAnimationState`, `AnimationLayerSignature`
/// and `AnimationInterfaceKey`).
///
/// A data block's identity *is* its [Identifier]. [#makeObject] builds the real engine object **once**;
/// that object reads its id **live** from this holder's own registry key (see [#currentId]), so a
/// rename (which only re-keys the holder via [ProjectObjectRegistry#rekey]) is reflected by the *same*
/// instance — a pure name change, no object regeneration. A type that also carries properties reads
/// those live off its holder the same way, and declares them for source export through
/// [#sourceProperties].
///
/// These holders live only inside the project's `ProjectObjectRegistry` while the editor is open —
/// they are never pushed into the engine `EnumerableDataBlockManager`. The editor lists data blocks
/// by *merging* the manager's hardcoded values with these project-scoped entries.
public abstract class DataBlockHolder<T extends EnumerableDataBlock> extends AbstractDataHolder<T> {
    /// Fallback id used only before the holder is bound to a registry key.
    private static final Identifier UNBOUND = AkytheraLabs.identifier("unbound_data_block");

    /// One authored property of a data block as the generated enum carries it: the field a constant
    /// stores it in, the interface method that reads it back, and the Java expression this holder's own
    /// value lowers to.
    public record SourceProperty(String type, String field, String accessor, String value) {}

    @Override
    public void readFrom(final T object) {
        // A property-less block's identity is the identifier, owned by the registry.
    }

    /// The properties the generated enum carries beyond its identifier, in constructor-parameter
    /// order. Empty — the default — is the property-less form, whose export is unchanged.
    ///
    /// `FreemarkerExporter` takes the field / accessor shape from a default instance of the type and
    /// each [SourceProperty#value] from the constant it belongs to.
    public List<SourceProperty> sourceProperties() {
        return List.of();
    }

    @Override
    public final T makeObject() {
        return instantiate();
    }

    /// Build the concrete engine object. Its `identifier()` / `toString()` must read [#currentId]
    /// **live**, so a later rekey is reflected without rebuilding the object.
    protected abstract T instantiate();

    /// This holder's current registry-key identifier (assigned/updated by the registry).
    protected final Identifier currentId() {
        ResourceKey<T> key = getOwnResourceKey();
        return key != null ? key.identifier() : UNBOUND;
    }

    /// `Identifier` &lt;-&gt; [Holder] codec for referencing an editable data block by id while keeping
    /// a live [Holder] in memory. Resolves through **project &cup; manager** (see
    /// [AkytheraProject#getDataBlockHolder]) so editor-created (labs) blocks resolve too; an id
    /// found in neither decodes to a **dangling** holder (see
    /// [AkytheraProject#getDataBlockHolderOrDangling]) instead of failing — the referring file
    /// loads, the reference round-trips through its key on save, and the editor marks the
    /// unresolved element red. JSON stays the bare id string (byte-identical with the engine name
    /// codec).
    ///
    /// Editor-only — the referencing record (e.g. `StateMachineData`) is never serialized at engine
    /// runtime, so `getCurrentProject()` is always valid here.
    public static <V extends EnumerableDataBlock> Codec<Holder<V>> holderCodec(final EnumerableDataBlockManager<V> manager) {
        return Identifier.CODEC.xmap(
            id -> AkytheraProject.getCurrentProject().getDataBlockHolderOrDangling(manager, id),
            // Encode via the key (works even for an unbound holder — a block deleted mid-session —
            // so saving never throws); fall back to value() for a keyless engine direct holder.
            h -> h.unwrapKey().map(ResourceKey::identifier).orElseGet(() -> h.value().identifier()));
    }

    /// `Identifier` &lt;-&gt; data block **value** codec, for a holder field that stores the block itself
    /// rather than a [Holder] of it. Resolves through **project &cup; manager** the way
    /// [#holderCodec] does.
    ///
    /// Unlike [#holderCodec] this cannot dangle — there is no value to stand in for a missing block —
    /// so an unresolved id fails the decode and the referring component is salvaged by the decode
    /// guard.
    public static <V extends EnumerableDataBlock> Codec<V> blockCodec(final EnumerableDataBlockManager<V> manager) {
        return Identifier.CODEC.flatXmap(
            id -> {
                Holder<V> holder = AkytheraProject.getCurrentProject().getDataBlockHolder(manager, id);

                return holder == null
                    ? DataResult.error(() -> "Unknown data block " + id + " in " + manager.directoryKey().identifier())
                    : DataResult.success(holder.value());
            },
            block -> {
                Identifier id = block.identifier();

                return id == null
                    ? DataResult.error(() -> "Data block is no longer registered in " + manager.directoryKey().identifier())
                    : DataResult.success(id);
            }
        );
    }
}