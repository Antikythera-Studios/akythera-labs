package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.AnimSequenceInterfaceNodeWidget;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// A replaceable **sequence** slot; its default animation is an animation sequence.
public record AnimSequenceInterfaceNodeData(
    Holder<AnimationInterfaceKey> interfaceKey,
    Optional<Holder<AnimationSequence>> defaultAnimation
) implements AnimInterfaceNodeData {

    public static final MapCodec<AnimSequenceInterfaceNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                DataBlockHolder.holderCodec(AnimationInterfaceKey.ENUM_MANAGER).fieldOf("variable")
                    .forGetter(AnimSequenceInterfaceNodeData::interfaceKey),
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).optionalFieldOf("asset")
                    .forGetter(AnimSequenceInterfaceNodeData::defaultAnimation)
            ).apply(instance, AnimSequenceInterfaceNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new AnimSequenceInterfaceNodeWidget(
            ctx.font(), interfaceKey, defaultAnimation.orElse(null), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return lower(in.id(), "play_anim_sequence_interface", List.of());
    }
}
