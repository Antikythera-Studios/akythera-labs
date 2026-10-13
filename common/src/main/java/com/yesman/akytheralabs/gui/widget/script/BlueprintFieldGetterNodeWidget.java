package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.BlueprintFieldGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Reads a [BlueprintField] of the script owner: one output, typed by the field.
public final class BlueprintFieldGetterNodeWidget extends AbstractNodeWidget {
    private final String field;
    private final @Nullable Class<?> type;

    public BlueprintFieldGetterNodeWidget(Font font, String field, @Nullable Class<?> type, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_GET_FIELD, field), graphX, graphY);

        this.field = field;
        this.type = type;

        setTitleColor(NodeColors.titleTint(type == null ? Object.class : type));

        addOutput(new Pin(field, NodeColors.forTypeOrWildcard(type), false, type));

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
        return new BlueprintFieldGetterNodeData(field);
    }
}
