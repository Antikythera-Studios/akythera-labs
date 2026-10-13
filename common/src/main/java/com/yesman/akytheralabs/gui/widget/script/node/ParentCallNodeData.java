package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ParentCallNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Calls the implementation the canvas' function overrides, with the arguments the function received.
/// Input 0 and output 0 are execution, and output 1, present when the function returns a value, is the
/// parent's answer, held in the local [#localName].
public record ParentCallNodeData() implements FlowNodeData {
    public static final MapCodec<ParentCallNodeData> CODEC = MapCodec.unit(new ParentCallNodeData());

    private static final int VALUE_PIN = 1;

    /// The local the call on row `nodeId` holds its answer in.
    public static String localName(int nodeId) {
        return "parent_" + nodeId;
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ParentCallNodeWidget(ctx.font(), ctx.enclosingSignature(), ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        ParentCallNode call = new ParentCallNode(self.nodeId());
        FlowNode statement = emitter.consumed(self.nodeId(), VALUE_PIN)
            ? new ScriptVariableSetterNode(localName(self.nodeId()), call)
            : call;

        FlowStatement nextSlot = self.child(0);

        return emitter.isEmpty(nextSlot) ? statement : new SequenceNode(List.of(statement, emitter.emit(nextSlot)));
    }

    @Override
    public @Nullable PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return fromPin == VALUE_PIN ? new ContextVariableAccessNode(localName(in.id())) : null;
    }
}
