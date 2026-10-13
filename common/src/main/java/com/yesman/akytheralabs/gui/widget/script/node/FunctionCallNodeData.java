package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.FunctionCallNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.FunctionCallNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Calls the function [#function] the blueprint declares. Input 0 and output 0 are execution, inputs 1..
/// its parameters, and output 1, present when it returns a value, its answer, held in the local
/// [#localName].
public record FunctionCallNodeData(String function) implements FlowNodeData {
    public static final MapCodec<FunctionCallNodeData> CODEC =
        Codec.STRING.fieldOf("function").xmap(FunctionCallNodeData::new, FunctionCallNodeData::function);

    /// Pin 0 is the execution input, so the first parameter sits at pin 1.
    private static final int PARAM_OFFSET = 1;

    private static final int VALUE_PIN = 1;

    /// The local the call on row `nodeId` holds its answer in.
    public static String localName(int nodeId) {
        return "call_" + nodeId;
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new FunctionCallNodeWidget(ctx.font(), function, ctx.members().method(function), ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        BlueprintSignature signature = skeleton.members().method(function);
        int count = signature == null ? 0 : signature.parameters().size();

        for (int slot = 0; slot < count; slot++) skeleton.readsInput(self, PARAM_OFFSET + slot);

        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        BlueprintSignature signature = emitter.members().method(function);
        List<PureNode> arguments = new ArrayList<>();

        if (signature != null) {
            for (int slot = 0; slot < signature.parameters().size(); slot++) {
                arguments.add(emitter.compileInputOr(
                    self.nodeId(), PARAM_OFFSET + slot, signature.parameters().get(slot).type()));
            }
        }

        FunctionCallNode call = new FunctionCallNode(function, arguments, self.nodeId());
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
