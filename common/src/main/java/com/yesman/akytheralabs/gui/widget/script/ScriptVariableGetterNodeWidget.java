package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.VariableGetterNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/// Widget for a [ScriptVariableGetterNode]: a pure node that reads a script member variable. No execution
/// pins and no inputs — just one output, colored and typed by the variable, labeled with the variable name.
/// The title bar is tinted to match the type, like its setter counterpart.
public final class ScriptVariableGetterNodeWidget extends AbstractNodeWidget implements VariableNodeWidget {
    private String variableName;
    private Class<?> variableType;

    public ScriptVariableGetterNodeWidget(Font font, Component title, String variableName, Class<?> variableType, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        this.variableName = variableName;
        this.variableType = variableType;

        setTitleColor(NodeColors.titleTint(variableType));

        addOutput(new Pin(variableName, NodeColors.forType(variableType), false, variableType));

        relayout();
    }

    @Override
    public void applyVariableType(Class<?> newType) {
        this.variableType = newType;
        setTitleColor(NodeColors.titleTint(newType));
        setOutputPin(0, new Pin(variableName, NodeColors.forType(newType), false, newType));
        relayout();
    }

    @Override
    public void applyVariableName(String newName) {
        this.variableName = newName;
        // The getter's title is the "Get <var>" label, and its sole output pin is labeled
        // with the variable name — both follow the rename.
        setMessage(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, newName));
        setOutputPin(0, new Pin(newName, NodeColors.forType(variableType), false, variableType));
        relayout();
    }

    public String variableName() {
        return variableName;
    }

    public Class<?> variableType() {
        return variableType;
    }

    @Override
    public NodeData toData() {
        return new VariableGetterNodeData(variableName, Optional.ofNullable(scriptTypeOf(variableType)));
    }
}
