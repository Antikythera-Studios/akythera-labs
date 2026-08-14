package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlendSpacePlayerNodeWidget;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/// Plays one blendspace, its coordinate driven by the two float inputs. The inputs are wrapped as
/// live `Supplier` updaters so they keep tracking after construction.
///
/// The blendspace is the target's **shared live holder** — see [AnimSequencePlayerNodeData].
public record BlendSpacePlayerNodeData(Holder<BlendSpace2D> blendSpace) implements NodeData {
    public static final MapCodec<BlendSpacePlayerNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY).fieldOf("asset")
                    .forGetter(BlendSpacePlayerNodeData::blendSpace)
            ).apply(instance, BlendSpacePlayerNodeData::new)
    );

    /// The bound blendspace's current id.
    public Identifier blendSpaceId() {
        return EditorHolderCodecs.entryId(blendSpace);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new BlendSpacePlayerNodeWidget(ctx.font(), blendSpace, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("play_blend_space_2d"),
            List.of(
                AnimGraphFunctions.stringLiteral(blendSpaceId().toString()),
                new SupplierAdapterNode(in.valueOr(0, float.class)),
                new SupplierAdapterNode(in.valueOr(1, float.class))),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return EditorHolderCodecs.referenceOf(blendSpace);
    }

    @Override
    public Stream<Identifier> missingReferences() {
        return EditorHolderCodecs.missingOf(blendSpace);
    }
}
