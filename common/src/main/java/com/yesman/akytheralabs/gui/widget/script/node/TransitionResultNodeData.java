package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.TransitionResultNodeWidget;
import net.minecraft.network.chat.Component;

/// A result canvas' terminal node: the value wired into its input is the canvas' result (a
/// transition's `Can Enter Transition` boolean, or a component getter's own type). A sink.
public record TransitionResultNodeData() implements NodeData {
    public static final MapCodec<TransitionResultNodeData> CODEC = MapCodec.unit(new TransitionResultNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new TransitionResultNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_TRANSITION_RESULT),
            ctx.x(), ctx.y());
    }
}
