package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ParentCallNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Calls the implementation the canvas' function overrides: execution in and out, plus the parent's answer
/// when the function returns a value.
public final class ParentCallNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final @Nullable BlueprintSignature function;

    public ParentCallNodeWidget(Font font, @Nullable BlueprintSignature function, int graphX, int graphY) {
        super(
            font,
            Component.translatable(
                LangKeys.GUI_BLUEPRINT_NODE_PARENT_CALL, function == null ? Component.literal("?") : function.title()),
            graphX, graphY
        );

        this.function = function;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", NodeColors.EXEC, true));

        if (function != null && function.returnType() != null) {
            addOutput(new Pin("", NodeColors.forType(function.returnType()), false, function.returnType()));
        }

        relayout();
    }

    @Override
    public List<Component> intrinsicErrors() {
        return function == null || function.kind() != BlueprintSignature.Kind.OVERRIDE
            ? List.of(Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_PARENT_OUTSIDE_OVERRIDE))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new ParentCallNodeData();
    }
}
