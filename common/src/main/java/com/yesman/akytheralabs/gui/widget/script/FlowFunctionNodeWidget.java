package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/// Widget for a [com.yesman.akythera.api.data.modularize.node.FlowFunctionNode]:
/// executes a single non-pure [ScriptFunction]. The left side carries the execution
/// input and one data pin per parameter (the owner slot of a member function is shown
/// as "Target"); the right side carries the execution output plus a "Return Value"
/// data pin when the function returns a value. Visual only — pins are not wired this stage.
public final class FlowFunctionNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final ScriptFunction function;

    public FlowFunctionNodeWidget(Font font, ScriptFunction function, int graphX, int graphY) {
        super(font, Component.literal(function.metadata().label()), graphX, graphY);

        this.function = function;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));

        FunctionSignature signature = function.functionSignature();
        List<Class<?>> parameters = signature.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            Class<?> type = parameters.get(i);
            String label = function.isMember() && i == 0 ? "Target" : type.getSimpleName();
            addInput(new Pin(label, NodeColors.forType(type), false, type));
        }

        addOutput(new Pin("", NodeColors.EXEC, true));

        Class<?> returnType = signature.returnType();
        if (returnType != void.class && returnType != Void.class) {
            addOutput(new Pin("Return Value", NodeColors.forType(returnType), false, returnType));
        }

        relayout();
    }

    public ScriptFunction function() {
        return function;
    }
}
