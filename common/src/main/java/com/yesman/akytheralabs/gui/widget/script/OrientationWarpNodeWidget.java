package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.OrientationWarpNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class OrientationWarpNodeWidget extends AbstractPoseControlNodeWidget {
    public OrientationWarpNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Orientation Warp"), graphX, graphY);

        addInput(new Pin("Hip", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Spine", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Angle", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new OrientationWarpNodeData();
    }
}
