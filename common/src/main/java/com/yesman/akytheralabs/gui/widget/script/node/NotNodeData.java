package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.InvertBooleanNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.NotNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;

/// Inverts the boolean on data input 0.
public record NotNodeData() implements NodeData {
    public static final MapCodec<NotNodeData> CODEC = MapCodec.unit(new NotNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new NotNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new InvertBooleanNode(in.valueOr(0, boolean.class), in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0);
    }
}
