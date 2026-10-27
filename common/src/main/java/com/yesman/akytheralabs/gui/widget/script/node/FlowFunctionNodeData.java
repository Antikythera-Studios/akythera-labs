package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.CallbackAdapterNode;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.NullNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.FlowFunctionNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Calls one non-pure [ScriptFunction]. Input pin 0 is execution and the pins after it are its data parameters;
/// output pin 0 is execution, whose chain becomes the call's `next`, and the pins after it are its callback
/// parameters, each a sub-flow passed as a [CallbackAdapterNode], or [NullNode] when unwired.
public record FlowFunctionNodeData(ScriptFunction function) implements FlowNodeData {
    public static final MapCodec<FlowFunctionNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("function")
                    .forGetter(FlowFunctionNodeData::function)
            ).apply(instance, FlowFunctionNodeData::new)
    );

    /// Pin 0 is the execution input, so the first data parameter sits at pin 1.
    private static final int PARAM_OFFSET = 1;

    /// Output pin 0 is the execution output, so the first callback sits at pin 1.
    private static final int CALLBACK_OFFSET = 1;

    /// Whether a parameter of `type` is a callback, drawn as an execution output rather than a data input.
    public static boolean isCallback(Class<?> type) {
        return Runnable.class.isAssignableFrom(type);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new FlowFunctionNodeWidget(ctx.font(), function, ctx.x(), ctx.y());
    }

    /// Children: the `next` chain, then one sub-flow per callback.
    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        List<Class<?>> parameters = function.functionSignature().parameters();
        int dataPin = PARAM_OFFSET;

        for (Class<?> type : parameters) {
            if (!isCallback(type)) skeleton.readsInput(self, dataPin++);
        }

        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));

        int callbackPin = CALLBACK_OFFSET;

        for (Class<?> type : parameters) {
            if (isCallback(type)) skeleton.chainInto(self.nodeId(), callbackPin++, skeleton.newSequenceChild(self));
        }
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        FlowStatement nextSlot = self.child(0);
        @Nullable FlowNode next = emitter.isEmpty(nextSlot) ? null : emitter.emit(nextSlot);

        FunctionSignature signature = function.functionSignature();
        List<PureNode> params = new ArrayList<>(signature.parameters().size());
        int dataPin = PARAM_OFFSET;
        int callback = CALLBACK_OFFSET;

        for (int i = 0; i < signature.parameters().size(); i++) {
            Class<?> type = signature.parameters().get(i);

            if (!isCallback(type)) {
                params.add(emitter.compileInputOr(self.nodeId(), dataPin++, type));
                continue;
            }

            FlowStatement body = self.child(callback++);

            if (emitter.isEmpty(body)) {
                params.add(NullNode.INSTANCE);
                continue;
            }

            // Each callback records under its own site, e.g. `onPossessed/started`
            String site = emitter.canvasKey().equals(ScriptNode.NO_CANVAS)
                ? ScriptNode.NO_CANVAS
                : emitter.canvasKey() + ScriptCaller.CALLBACK_SEPARATOR + signature.parameterNames().get(i);

            params.add(new CallbackAdapterNode(emitter.emit(body), site));
        }

        return new FlowFunctionNode(function, params, next, self.nodeId());
    }
}
