package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.AnimSequencePlayerNodeWidget;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/// Plays one fixed animation sequence: no inputs, one pose output.
///
/// The sequence is the target's **shared live holder**, so a rename of that entry is reflected here
/// with no rewrite — [#sequenceId] reads the new id and the next save writes it.
public record AnimSequencePlayerNodeData(Holder<AnimationSequence> sequence) implements NodeData {
    public static final MapCodec<AnimSequencePlayerNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).fieldOf("asset")
                    .forGetter(AnimSequencePlayerNodeData::sequence)
            ).apply(instance, AnimSequencePlayerNodeData::new)
    );

    /// The bound sequence's current id.
    public Identifier sequenceId() {
        return EditorHolderCodecs.entryId(sequence);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new AnimSequencePlayerNodeWidget(ctx.font(), sequence, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("play_anim_sequence"),
            List.of(AnimGraphFunctions.stringLiteral(sequenceId().toString())),
            in.id());
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return EditorHolderCodecs.referenceOf(sequence);
    }

    @Override
    public Stream<Identifier> missingReferences() {
        return EditorHolderCodecs.missingOf(sequence);
    }
}
