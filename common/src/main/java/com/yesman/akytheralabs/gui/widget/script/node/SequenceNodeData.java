package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.SequenceNodeWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Fans one execution input out to [#outputs] ordered execution outputs ("Then 0", "Then 1", …).
/// Each output's chain becomes one block of the emitted [SequenceNode].
public record SequenceNodeData(int outputs) implements FlowNodeData {
    public static final MapCodec<SequenceNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.INT.optionalFieldOf("outputs", 0).forGetter(SequenceNodeData::outputs)
            ).apply(instance, SequenceNodeData::new)
    );

    /// At least one output, whatever the row says.
    public int pinCount() {
        return Math.max(1, outputs);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        SequenceNodeWidget widget = new SequenceNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE),
            ctx.x(), ctx.y());

        while (widget.outputPinCount() < pinCount()) widget.addOutputPin();
        while (widget.outputPinCount() > pinCount()) widget.removeOutputPin(widget.outputPinCount() - 1);

        return widget;
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        for (int pin = 0; pin < pinCount(); pin++) skeleton.chainInto(self.nodeId(), pin, self);
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        List<FlowNode> blocks = new ArrayList<>(self.children().size());
        for (FlowStatement child : self.children()) blocks.add(emitter.emit(child));

        return new SequenceNode(blocks);
    }
}
