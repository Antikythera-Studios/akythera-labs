package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.AdditiveBlendNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/// Lays the additive pose wired into it over the base pose, scaled by the `Alpha` input. A fieldless
/// row, as [TwoPoseBlendNodeData] is.
public record AdditiveBlendNodeData() implements NodeData {
    public static final MapCodec<AdditiveBlendNodeData> CODEC = MapCodec.unit(new AdditiveBlendNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new AdditiveBlendNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("apply_additive"),
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
