package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// The terminal node of a transition's condition graph (UE's "Result" node): a single boolean
/// `Can Enter Transition` input and no output. The boolean expression wired into it decides whether
/// the state machine may take this transition.
///
/// The condition graph is pure data-flow (no execution), so like [OutputPoseNodeWidget] this is the
/// root and cannot be deleted.
///
/// Compile rule (see the graph compilers): the transition lowers to the [com.yesman.akythera.api.data.modularize.node.PureNode]
/// wired into `Can Enter Transition` (engine plan, Stage 3c).
public final class TransitionResultNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF3A2020;
    private static final String CAN_ENTER_LABEL = "Can Enter Transition";

    public TransitionResultNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(new Pin(CAN_ENTER_LABEL, NodeColors.forType(boolean.class), false, boolean.class));

        relayout();
    }

    @Override
    public boolean isDeletable() {
        return false;
    }
}
