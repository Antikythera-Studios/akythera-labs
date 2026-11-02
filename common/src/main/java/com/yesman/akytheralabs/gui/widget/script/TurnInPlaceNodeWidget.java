package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.TurnInPlaceNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class TurnInPlaceNodeWidget extends AbstractPoseControlNodeWidget {
    public TurnInPlaceNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Turn In Place"), graphX, graphY);

        addInput(new Pin("Yaw", NodeColors.forType(float.class), false, float.class));
        addInput(new Pin("Weight", NodeColors.forType(float.class), false, float.class));
        addInput(new Pin("Step Time", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new TurnInPlaceNodeData();
    }
}
