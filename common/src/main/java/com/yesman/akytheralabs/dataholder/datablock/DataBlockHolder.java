package com.yesman.akytheralabs.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/// Editor-side holder for one editable [EnumerableDataBlock] type (those registered in
/// `EnumerableDataBlockSync#LISTENERS` — currently `FSMAnimationState` and
/// `AnimationLayerSignature`).
///
/// A data block is property-less: its identity *is* its [Identifier]. [#makeObject] builds the
/// real engine object **once**; that object reads its id **live** from this holder's own registry
/// key (see [#currentId]), so a rename (which only re-keys the holder via
/// [com.yesman.akytheralabs.dataholder.ProjectObjectRegistry#rekey]) is reflected by the *same*
/// instance — a pure name change, no object regeneration.
///
/// These holders live only inside the project's `ProjectObjectRegistry` while the editor is open —
/// they are never pushed into the engine `EnumerableDataBlockManager`. The editor lists data blocks
/// by *merging* the manager's hardcoded values with these project-scoped entries.
public abstract class DataBlockHolder<T extends EnumerableDataBlock> extends AbstractDataHolder<T> {
    /// Fallback id used only before the holder is bound to a registry key.
    private static final Identifier UNBOUND = AkytheraLabs.identifier("unbound_data_block");

    @Override
    public void readFrom(final T object) {
        // No editable properties — identity is the identifier, owned by the registry.
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
    /// [AkytheraProject#getDataBlockHolder]) so editor-created (labs) blocks resolve too. JSON stays
    /// the bare id string (byte-identical with the engine name codec).
    ///
    /// Editor-only — the referencing record (e.g. `StateMachineData`) is never serialized at engine
    /// runtime, so `getCurrentProject()` is always valid here. Decode is strict: a reference present
    /// in neither the project nor the manager fails to decode (matches the engine `getNameCodec`).
    public static <V extends EnumerableDataBlock> Codec<Holder<V>> holderCodec(final EnumerableDataBlockManager<V> manager) {
        return Identifier.CODEC.comapFlatMap(
            id -> {
                Holder<V> h = AkytheraProject.getCurrentProject().getDataBlockHolder(manager, id);
                return h != null
                    ? DataResult.success(h)
                    : DataResult.error(() -> "Unknown data block " + id + " in " + manager.directoryKey().identifier());
            },
            // Encode via the key (works even for an unbound holder — a block deleted mid-session —
            // so saving never throws); fall back to value() for a keyless engine direct holder.
            h -> h.unwrapKey().map(k -> k.identifier()).orElseGet(() -> h.value().identifier()));
    }
}