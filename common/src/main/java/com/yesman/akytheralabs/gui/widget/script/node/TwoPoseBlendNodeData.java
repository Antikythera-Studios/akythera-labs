package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.TwoPoseBlendNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/// Interpolates between the two poses wired into it by the `Alpha` input. Everything the node blends
/// with is wired, so the row itself carries no field.
public record TwoPoseBlendNodeData() implements NodeData {
    public static final MapCodec<TwoPoseBlendNodeData> CODEC = MapCodec.unit(new TwoPoseBlendNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new TwoPoseBlendNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("blend_two_poses"),
            List.of(
                in.valueOr(0, AnimationDriver.class),
                in.valueOr(1, AnimationDriver.class),
                new SupplierAdapterNode(in.valueOr(2, float.class), in.canvasKey())),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1, 2);
    }
}
