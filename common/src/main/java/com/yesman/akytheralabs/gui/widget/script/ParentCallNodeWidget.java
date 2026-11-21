package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ParentCallNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// Calls the implementation the running method overrides: execution in and out, plus the parent's answer
/// when the method returns a value.
public final class ParentCallNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final @Nullable BlueprintSignature function;

    /// The event whose flow the node sits in on an Event Graph. Null on a function canvas.
    private final @Nullable String event;

    public ParentCallNodeWidget(Font font, @Nullable BlueprintSignature function, @Nullable String event, int graphX, int graphY) {
        super(
            font,
            Component.translatable(
                LangKeys.GUI_BLUEPRINT_NODE_PARENT_CALL, function == null ? Component.literal("?") : function.title()),
            graphX, graphY
        );

        this.function = function;
        this.event = event;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", NodeColors.EXEC, true));

        if (function != null && function.returnType() != null) {
            addOutput(new Pin("", NodeColors.forType(function.returnType()), false, function.returnType()));
        }

        relayout();
    }

    /// A Parent node on an event of a blueprint with no parent script names that cause.
    @Override
    public List<Component> intrinsicErrors() {
        if (function != null && function.parentCallable()) return List.of();

        return List.of(Component.translatable(
            event != null ? LangKeys.GUI_BLUEPRINT_ERROR_PARENT_WITHOUT_PARENT : LangKeys.GUI_BLUEPRINT_ERROR_PARENT_OUTSIDE_OVERRIDE));
    }

    @Override
    public NodeData toData() {
        return new ParentCallNodeData(Optional.ofNullable(event));
    }
}
