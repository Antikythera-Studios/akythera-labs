package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.OutputPoseNodeWidget;
import net.minecraft.network.chat.Component;

/// The Anim Graph's terminal node: the driver wired into its `Result` input is the graph's pose. A
/// sink — the pose compiler finds it and reads its input, and it produces no value of its own.
public record OutputPoseNodeData() implements NodeData {
    public static final MapCodec<OutputPoseNodeData> CODEC = MapCodec.unit(new OutputPoseNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new OutputPoseNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_OUTPUT_POSE),
            ctx.x(), ctx.y());
    }
}
