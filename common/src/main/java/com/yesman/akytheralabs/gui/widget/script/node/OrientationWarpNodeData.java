package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.OrientationWarpNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

public record OrientationWarpNodeData() implements NodeData {
    public static final MapCodec<OrientationWarpNodeData> CODEC = MapCodec.unit(new OrientationWarpNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new OrientationWarpNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("orientation_warp"),
            List.of(
                new ContextVariableAccessNode("animator"),
                in.valueOr(0, AnimationDriver.class),
                in.valueOr(1, String.class),
                in.valueOr(2, String.class),
                new SupplierAdapterNode(in.valueOr(3, float.class), in.canvasKey())),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1, 2, 3);
    }
}
