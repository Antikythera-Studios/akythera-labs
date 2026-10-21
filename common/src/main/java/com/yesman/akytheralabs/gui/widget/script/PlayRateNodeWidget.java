package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PlayRateNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class PlayRateNodeWidget extends AbstractPoseControlNodeWidget {
    public PlayRateNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Play Rate"), graphX, graphY);

        addInput(new Pin("Rate", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new PlayRateNodeData();
    }
}
