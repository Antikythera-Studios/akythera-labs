package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.VariableSetterNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/// Widget for a [ScriptVariableSetterNode]: sets a script member variable. The value-in data pin and the
/// value-out passthrough pin are colored by the variable's type, and the title bar is tinted to match.
public final class ScriptVariableSetterNodeWidget extends AbstractNodeWidget implements VariableNodeWidget {
    private String variableName;
    private Class<?> variableType;

    public ScriptVariableSetterNodeWidget(Font font, Component title, String variableName, Class<?> variableType, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        this.variableName = variableName;
        this.variableType = variableType;

        int typeColor = NodeColors.forType(variableType);
        setTitleColor(NodeColors.titleTint(variableType));

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(new Pin(variableName, typeColor, false, variableType));

        addOutput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", typeColor, false, variableType));

        relayout();
    }

    @Override
    public void applyVariableType(Class<?> newType) {
        this.variableType = newType;
        int typeColor = NodeColors.forType(newType);
        setTitleColor(NodeColors.titleTint(newType));
        // Data pins only — the exec pins (input 0 / output 0) are untouched. The value-in
        // pin keeps the variable-name label; the passthrough output stays unlabeled.
        setInputPin(1, new Pin(variableName, typeColor, false, newType));
        setOutputPin(1, new Pin("", typeColor, false, newType));
        relayout();
    }

    @Override
    public void applyVariableName(String newName) {
        this.variableName = newName;
        // The setter's title is a static "Set"; only the value-in data pin (input 1) carries
        // the variable-name label, so the rename touches that pin alone.
        setInputPin(1, new Pin(newName, NodeColors.forType(variableType), false, variableType));
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
        return new VariableSetterNodeData(variableName, Optional.ofNullable(scriptTypeOf(variableType)));
    }
}
