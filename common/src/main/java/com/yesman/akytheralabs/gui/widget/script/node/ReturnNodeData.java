package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ReturnNode;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ReturnNodeWidget;

import java.util.Optional;

/// Ends the function the canvas implements. Input 0 is execution, and input 1, present when the function
/// returns a value, is what it answers.
public record ReturnNodeData() implements FlowNodeData {
    public static final MapCodec<ReturnNodeData> CODEC = MapCodec.unit(new ReturnNodeData());

    private static final int VALUE_PIN = 1;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ReturnNodeWidget(ctx.font(), ctx.enclosingSignature(), ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, VALUE_PIN);
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        BlueprintSignature function = emitter.enclosingFunction();
        Optional<PureNode> value = function == null || function.returnType() == null
            ? Optional.empty()
            : Optional.of(emitter.compileInputOr(self.nodeId(), VALUE_PIN, function.returnType()));

        return new ReturnNode(value, self.nodeId());
    }
}
