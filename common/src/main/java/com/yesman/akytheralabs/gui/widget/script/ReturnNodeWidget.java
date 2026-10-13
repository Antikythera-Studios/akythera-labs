package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ReturnNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Ends the function the canvas implements: an execution input, plus an input typed by what the function
/// returns when it returns a value.
public final class ReturnNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF3A2020;

    public ReturnNodeWidget(Font font, @Nullable BlueprintSignature function, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_RETURN), graphX, graphY);

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));

        if (function != null && function.returnType() != null) {
            addInput(new Pin("Return Value", NodeColors.forType(function.returnType()), false, function.returnType()));
        }

        relayout();
    }

    /// A canvas holds at most one Return, which every ending flow wires into. A canvas without one returns
    /// nothing.
    @Override
    public boolean isCopyable() {
        return false;
    }

    @Override
    public NodeData toData() {
        return new ReturnNodeData();
    }
}
