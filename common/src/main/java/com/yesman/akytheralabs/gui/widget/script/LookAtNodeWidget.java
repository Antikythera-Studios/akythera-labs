package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.LookAtNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class LookAtNodeWidget extends AbstractPoseControlNodeWidget {
    public LookAtNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Look At"), graphX, graphY);

        addInput(new Pin("Bone", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Rotation", NodeColors.forType(Vec3.class), false, Vec3.class));
        addInput(new Pin("Animation Share", NodeColors.forType(float.class), false, float.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new LookAtNodeData();
    }
}
