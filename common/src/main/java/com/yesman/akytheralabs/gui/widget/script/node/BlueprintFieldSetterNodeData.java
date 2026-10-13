package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.BlueprintFieldGetterNode;
import com.yesman.akythera.api.data.modularize.node.BlueprintFieldSetterNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlueprintFieldSetterNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Writes the [BlueprintField] [#field] of the script owner. Data input 1 is the value, and the data output
/// reads the field back.
public record BlueprintFieldSetterNodeData(String field) implements FlowNodeData {
    public static final MapCodec<BlueprintFieldSetterNodeData> CODEC =
        Codec.STRING.fieldOf("field").xmap(BlueprintFieldSetterNodeData::new, BlueprintFieldSetterNodeData::field);

    private static final int VALUE_PIN = 1;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new BlueprintFieldSetterNodeWidget(ctx.font(), field, ctx.members().fieldType(field), ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, VALUE_PIN);
        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        BlueprintFieldSetterNode setter = new BlueprintFieldSetterNode(
            field, emitter.compileInputOr(self.nodeId(), VALUE_PIN, emitter.members().fieldType(field)), self.nodeId());

        FlowStatement nextSlot = self.child(0);

        return emitter.isEmpty(nextSlot) ? setter : new SequenceNode(List.of(setter, emitter.emit(nextSlot)));
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new BlueprintFieldGetterNode(field, in.id());
    }
}
