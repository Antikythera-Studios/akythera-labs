package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.TransitionResultNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// The terminal node of a result graph (UE's "Result" node): a single typed input and no output. A
/// transition's condition graph is the boolean case, labelled `Can Enter Transition`; a component's
/// value getter takes its own type through [#setResultType].
///
/// A result graph is pure data-flow (no execution), so like [OutputPoseNodeWidget] this is the root
/// and cannot be deleted.
///
/// Compile rule (see the graph compilers): the graph lowers to the [PureNode] wired into the input.
public final class TransitionResultNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF3A2020;
    private static final String CAN_ENTER_LABEL = "Can Enter Transition";
    private static final String RESULT_LABEL = "Result";

    public TransitionResultNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(resultPin(boolean.class));

        relayout();
    }

    /// Re-types the result input. The type is structural (never persisted), so the host stamps it
    /// after seeding or importing the canvas.
    public void setResultType(Class<?> type) {
        setInputPin(0, resultPin(type));
        relayout();
    }

    private static Pin resultPin(Class<?> type) {
        return new Pin(
            type == boolean.class ? CAN_ENTER_LABEL : RESULT_LABEL,
            NodeColors.forType(type),
            false,
            type);
    }

    @Override
    public boolean isDeletable() {
        return false;
    }

    @Override
    public NodeData toData() {
        return new TransitionResultNodeData();
    }
}
