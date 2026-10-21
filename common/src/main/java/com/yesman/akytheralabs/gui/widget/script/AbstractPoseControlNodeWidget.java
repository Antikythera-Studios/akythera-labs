package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public abstract class AbstractPoseControlNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A4A3A;

    protected AbstractPoseControlNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));
    }
}
