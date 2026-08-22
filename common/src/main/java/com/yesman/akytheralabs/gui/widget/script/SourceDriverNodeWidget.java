package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.animgraph.SourceDriverNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.SourceDriverNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Widget for a [SourceDriverNode]: no execution pins and no inputs, one [AnimationDriver] output — the
/// driver of the state the transition leaves.
public final class SourceDriverNodeWidget extends AbstractNodeWidget {
    /// Shown on the node and in the suggestion list, so both name the same thing.
    public static final String TITLE = "Source State Driver";

    public SourceDriverNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal(TITLE), graphX, graphY);

        setTitleColor(NodeColors.titleTint(AnimationDriver.class));

        addOutput(new Pin("Driver", NodeColors.forType(AnimationDriver.class), false, AnimationDriver.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new SourceDriverNodeData();
    }
}
