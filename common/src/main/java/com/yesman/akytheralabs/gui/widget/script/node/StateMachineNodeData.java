package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.StateMachineNodeWidget;
import org.jspecify.annotations.Nullable;

/// A pose source backed by an animation state machine, whose states and transitions live in the
/// owning holder under [#machineName]. Lowers to whatever the hosting canvas makes of that machine —
/// nothing on a canvas that hosts none (see [PureCompileContext#stateMachine]).
public record StateMachineNodeData(String machineName) implements NodeData {
    public static final MapCodec<StateMachineNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("variable", "").forGetter(StateMachineNodeData::machineName)
            ).apply(instance, StateMachineNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new StateMachineNodeWidget(ctx.font(), machineName, ctx.x(), ctx.y());
    }

    @Override
    public @Nullable PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return in.ctx().stateMachine(machineName);
    }
}
