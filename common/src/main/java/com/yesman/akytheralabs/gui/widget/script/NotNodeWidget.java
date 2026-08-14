package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NotNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A boolean logical-NOT node (UE's "NOT"): one boolean input, one inverted boolean output. Used in
/// state-machine transition conditions (`transition_condition.png`) to invert a predicate.
///
/// Compile rule (see the graph compilers): emit the boolean-negation node — a `BasicOperatorNode` NOT (or a
/// `MathUtils#not` `ScriptFunction`) over the wired-in boolean (engine plan).
public final class NotNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    public NotNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("NOT"), graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));
        addOutput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new NotNodeData();
    }
}
