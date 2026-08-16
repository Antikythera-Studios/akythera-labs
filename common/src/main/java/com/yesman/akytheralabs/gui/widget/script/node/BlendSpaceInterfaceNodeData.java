package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlendSpaceInterfaceNodeWidget;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/// A replaceable **blendspace** slot; its default animation is a blendspace, and its two float inputs
/// drive the blend coordinate.
public record BlendSpaceInterfaceNodeData(
    Holder<AnimationInterfaceKey> interfaceKey,
    Optional<Holder<BlendSpace2D>> defaultAnimation
) implements AnimInterfaceNodeData {

    public static final MapCodec<BlendSpaceInterfaceNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                DataBlockHolder.holderCodec(AnimationInterfaceKey.ENUM_MANAGER).fieldOf("variable")
                    .forGetter(BlendSpaceInterfaceNodeData::interfaceKey),
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY).optionalFieldOf("asset")
                    .forGetter(BlendSpaceInterfaceNodeData::defaultAnimation)
            ).apply(instance, BlendSpaceInterfaceNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new BlendSpaceInterfaceNodeWidget(
            ctx.font(), interfaceKey, defaultAnimation.orElse(null), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return lower(in.id(), "play_blend_space_2d_interface", List.of(
            new SupplierAdapterNode(in.valueOr(0, float.class), in.canvasKey()),
            new SupplierAdapterNode(in.valueOr(1, float.class), in.canvasKey())));
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1);
    }
}
