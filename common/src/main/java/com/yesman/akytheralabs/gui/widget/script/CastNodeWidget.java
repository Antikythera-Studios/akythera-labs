package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.CastNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.CastNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Widget for a [CastNode]: an execution input plus an "Object" data input, continuing on the execution
/// output when the object is a [#type] and on "Cast Failed" otherwise. The "As" output carries the cast
/// object.
public final class CastNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F4A4A;

    private final ScriptType type;

    public CastNodeWidget(Font font, ScriptType type, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_CAST, typeName(type)), graphX, graphY);

        this.type = type;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(new Pin("Object", NodeColors.forType(Object.class), false, Object.class));

        addOutput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("Cast Failed", NodeColors.EXEC, true));
        addOutput(new Pin("As " + typeName(type), NodeColors.forType(type.type()), false, type.type()));

        relayout();
    }

    /// The name a cast to `type` shows in its title, its value pin and the "Cast To" suggestion.
    public static String typeName(ScriptType type) {
        return TypeConvertNodeWidget.typeName(type.type());
    }

    @Override
    public NodeData toData() {
        return new CastNodeData(type);
    }
}
