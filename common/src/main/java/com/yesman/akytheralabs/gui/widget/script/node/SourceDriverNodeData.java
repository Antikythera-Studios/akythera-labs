package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.SourceDriverNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.SourceDriverNodeWidget;
import org.jspecify.annotations.Nullable;

/// Yields the driver of the state a transition is leaving. Only a transition-condition canvas has
/// one bound, which is the only canvas the row is offered on.
public record SourceDriverNodeData() implements NodeData {
    public static final MapCodec<SourceDriverNodeData> CODEC = MapCodec.unit(new SourceDriverNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new SourceDriverNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return SourceDriverNode.INSTANCE;
    }
}
