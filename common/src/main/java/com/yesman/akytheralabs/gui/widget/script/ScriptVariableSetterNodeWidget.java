package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.VariableSetterNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Widget for a [ScriptVariableSetterNode]: sets a script member variable. The value-in data pin and the
/// value-out passthrough pin are colored by the variable's type, and the title bar is tinted to match.
public final class ScriptVariableSetterNodeWidget extends AbstractNodeWidget implements VariableNodeWidget {
    private String variableName;
    @Nullable
    private ScriptType variableType;

    public ScriptVariableSetterNodeWidget(
        Font font, Component title, String variableName, @Nullable ScriptType variableType, int graphX, int graphY
    ) {
        super(font, title, graphX, graphY);

        this.variableName = variableName;
        this.variableType = variableType;

        setTitleColor(NodeColors.titleTint(VariableNodeWidget.colorType(variableType)));

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(valuePin(variableName));

        addOutput(new Pin("", NodeColors.EXEC, true));
        addOutput(valuePin(""));

        relayout();
    }

    @Override
    public void applyVariableType(@Nullable ScriptType newType) {
        this.variableType = newType;
        setTitleColor(NodeColors.titleTint(VariableNodeWidget.colorType(newType)));
        // Data pins only — the exec pins (input 0 / output 0) are untouched. The value-in
        // pin keeps the variable-name label; the passthrough output stays unlabeled.
        setInputPin(1, valuePin(variableName));
        setOutputPin(1, valuePin(""));
        relayout();
    }

    @Override
    public void applyVariableName(String newName) {
        this.variableName = newName;
        // The setter's title is a static "Set"; only the value-in data pin (input 1) carries
        // the variable-name label, so the rename touches that pin alone.
        setInputPin(1, valuePin(newName));
        relayout();
    }

    /// A data pin carrying the variable's value, labeled `label`.
    private Pin valuePin(String label) {
        return new Pin(
            label, NodeColors.forType(VariableNodeWidget.colorType(variableType)), false,
            VariableNodeWidget.pinType(variableType), VariableNodeWidget.pinGenericType(variableType));
    }

    public String variableName() {
        return variableName;
    }

    public @Nullable ScriptType variableType() {
        return variableType;
    }

    @Override
    public NodeData toData() {
        return new VariableSetterNodeData(variableName, Optional.ofNullable(variableType));
    }
}
