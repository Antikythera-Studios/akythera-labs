package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.AnimLayerNodeWidget;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.stream.Stream;

/// Registers an `AnimationLayer` under [#signature]: the driver wired into `Source` becomes the
/// layer's base, and the pose output yields the layer's blended pose.
///
/// The layer is put into the animator's layer map at construct time by the pose compiler, and read
/// back from it when this node feeds the Output Pose — so the node itself lowers to no value.
public record AnimLayerNodeData(Holder<AnimationLayerSignature> signature) implements NodeData {
    public static final MapCodec<AnimLayerNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                DataBlockHolder.holderCodec(AnimationLayerSignature.ENUM_MANAGER).fieldOf("variable")
                    .forGetter(AnimLayerNodeData::signature)
            ).apply(instance, AnimLayerNodeData::new)
    );

    /// The signature's current id — its persistence key and what the compiler stores the layer under.
    public Identifier signatureId() {
        return EditorHolderCodecs.blockId(signature);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new AnimLayerNodeWidget(ctx.font(), signature, ctx.x(), ctx.y());
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return EditorHolderCodecs.referenceOf(signature);
    }

    @Override
    public Stream<Identifier> missingReferences() {
        return EditorHolderCodecs.missingOf(signature);
    }
}
