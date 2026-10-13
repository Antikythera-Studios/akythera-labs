package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.CastNode;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.CastNodeWidget;
import org.jspecify.annotations.Nullable;

/// Casts the value on data input 1 to [#type]. Input 0 is execution; output 0 continues when the value is
/// a [#type], output 1 ("Cast Failed") when it is not, and output 2 is the cast value, held in the local
/// [#localName]. Input 1 must be wired.
public record CastNodeData(ScriptType type) implements FlowNodeData {
    public static final MapCodec<CastNodeData> CODEC =
        AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("type").xmap(CastNodeData::new, CastNodeData::type);

    private static final int OBJECT_PIN = 1;
    private static final int VALUE_PIN = 2;

    /// The local the cast on row `nodeId` holds its value in.
    public static String localName(int nodeId) {
        return "cast_" + nodeId;
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new CastNodeWidget(ctx.font(), type, ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, OBJECT_PIN);

        FlowStatement whenSucceeded = skeleton.newSequenceChild(self);
        FlowStatement whenFailed = skeleton.newSequenceChild(self);
        skeleton.chainInto(self.nodeId(), 0, whenSucceeded);
        skeleton.chainInto(self.nodeId(), 1, whenFailed);
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        PureNode object = emitter.compileInput(self.nodeId(), OBJECT_PIN);

        if (object == null) {
            throw new IllegalStateException("Cast To " + type.type().getSimpleName() + " has no object wired");
        }

        return new CastNode(
            type, object, localName(self.nodeId()),
            emitter.emitArm(self.child(0)), emitter.emitArm(self.child(1)), self.nodeId());
    }

    @Override
    public @Nullable PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return fromPin == VALUE_PIN ? new ContextVariableAccessNode(localName(in.id())) : null;
    }
}
