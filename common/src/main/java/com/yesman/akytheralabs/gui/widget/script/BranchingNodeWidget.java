package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.BranchNode;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Widget for a [BranchNode]: an
/// execution input plus a boolean "Condition" data input that always expects a
/// pure node (no inline checkbox), branching to "True" / "False" execution outputs.
/// Visual only — pins are not wired this stage.
public final class BranchingNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF3A3A3A;

    public BranchingNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(new Pin("Condition", NodeColors.forType(boolean.class), false, boolean.class));

        addOutput(new Pin("True", NodeColors.EXEC, true));
        addOutput(new Pin("False", NodeColors.EXEC, true));

        relayout();
    }
}
