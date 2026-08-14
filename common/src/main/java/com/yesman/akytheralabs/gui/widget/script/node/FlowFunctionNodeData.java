package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.FlowFunctionNodeWidget;
import org.jspecify.annotations.Nullable;

/// Calls one non-pure [ScriptFunction]. Input pin 0 is execution and pins 1.. are its parameters;
/// output pin 0 is execution and the chain wired to it becomes the call's `next`.
public record FlowFunctionNodeData(ScriptFunction function) implements FlowNodeData {
    public static final MapCodec<FlowFunctionNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("function")
                    .forGetter(FlowFunctionNodeData::function)
            ).apply(instance, FlowFunctionNodeData::new)
    );

    /// Pin 0 is the execution input, so the first parameter sits at pin 1.
    private static final int PARAM_OFFSET = 1;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new FlowFunctionNodeWidget(ctx.font(), function, ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        int count = function.functionSignature().parameters().size();
        for (int slot = 0; slot < count; slot++) skeleton.readsInput(self, PARAM_OFFSET + slot);

        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        FlowStatement nextSlot = self.child(0);
        @Nullable FlowNode next = emitter.isEmpty(nextSlot) ? null : emitter.emit(nextSlot);

        return new FlowFunctionNode(
            function, emitter.compileParams(self.nodeId(), function, PARAM_OFFSET), next, self.nodeId());
    }
}
