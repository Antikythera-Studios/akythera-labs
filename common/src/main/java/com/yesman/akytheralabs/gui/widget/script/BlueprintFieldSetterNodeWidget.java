package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.BlueprintFieldSetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Writes a [BlueprintField] of the script owner: execution in and out, the value in, and the field read
/// back out.
public final class BlueprintFieldSetterNodeWidget extends AbstractNodeWidget {
    private final String field;
    private final @Nullable Class<?> type;

    public BlueprintFieldSetterNodeWidget(Font font, String field, @Nullable Class<?> type, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_SET_FIELD, field), graphX, graphY);

        this.field = field;
        this.type = type;

        int typeColor = NodeColors.forTypeOrWildcard(type);
        setTitleColor(NodeColors.titleTint(type == null ? Object.class : type));

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(new Pin(field, typeColor, false, type));

        addOutput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", typeColor, false, type));

        relayout();
    }

    @Override
    public List<Component> intrinsicErrors() {
        return type == null
            ? List.of(Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_UNKNOWN_FIELD, field))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new BlueprintFieldSetterNodeData(field);
    }
}
