package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.EntityPatchEntryNodeWidget;

/// An entity-patch flow origin. All three entry points (`onConstruct` / `onJoinWorld` / `update`) are
/// parameter-less, so they cannot be told apart by their outputs the way the animator script's
/// entries are — [#entryKind] is the explicit marker, persisted under the legacy `op` key, and
/// [com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder] roots each flow at the
/// matching entry through it.
public record EntityPatchEntryNodeData(EntityPatchEntryNodeWidget.Kind entryKind) implements EntryNodeData {
    public static final MapCodec<EntityPatchEntryNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.xmap(EntityPatchEntryNodeData::parseKind, Enum::name).fieldOf("op")
                    .forGetter(EntityPatchEntryNodeData::entryKind)
            ).apply(instance, EntityPatchEntryNodeData::new)
    );

    /// An unknown marker reads as `UPDATE` so the flow keeps a root.
    private static EntityPatchEntryNodeWidget.Kind parseKind(String name) {
        try {
            return EntityPatchEntryNodeWidget.Kind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return EntityPatchEntryNodeWidget.Kind.UPDATE;
        }
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new EntityPatchEntryNodeWidget(ctx.font(), entryKind, ctx.x(), ctx.y());
    }

    @Override
    public boolean hasDataOutputs() {
        return false;
    }
}
