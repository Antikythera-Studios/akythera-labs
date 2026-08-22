package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.OutputPoseNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// The terminal node of the Anim Graph (UE's "Output Pose"): a single [Pose]-typed `Result`
/// input and no output. The pose value wired into `Result` is what the animator's
/// [AnimatorScript#getFinalPose] returns.
///
/// Unlike the Event Graph, the Anim Graph is **data-flow**: there are no execution pins. The compiled
/// `poseGetter` [FlowNode] is derived by evaluating the [PureNode] sub-tree feeding this node's `Result`
/// input (see the graph compilers).
///
/// Like the Event Graph's entry nodes this is the graph's root and so cannot be deleted.
public final class OutputPoseNodeWidget extends AbstractNodeWidget {
    /// Brown title, matching UE's terminal-result node tint.
    private static final int TITLE_COLOR = 0xFF4A3A2A;

    public OutputPoseNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("Result", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The Anim Graph has exactly one Output Pose; without it the graph has no result.
    @Override
    public boolean isDeletable() {
        return false;
    }

    @Override
    public NodeData toData() {
        return new OutputPoseNodeData();
    }
}
