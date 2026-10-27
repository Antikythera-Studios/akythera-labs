package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.gui.widget.script.node.FlowFunctionNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/// Widget for a [FlowFunctionNode]: executes a single non-pure [ScriptFunction]. The left side carries the
/// execution input and one data pin per parameter (the owner slot of a member function is shown as "Target");
/// the right side carries the execution output, one execution pin per callback parameter, and a "Return Value"
/// data pin when the function returns a value.
public final class FlowFunctionNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final ScriptFunction function;

    /// The function's signature without its callback parameters, matching the data input pins.
    private final FunctionSignature dataSignature;

    /// The output pin of the return value, or -1 when the function returns nothing.
    private final int returnPin;

    public FlowFunctionNodeWidget(Font font, ScriptFunction function, int graphX, int graphY) {
        super(font, Component.literal(function.metadata().label()), graphX, graphY);

        this.function = function;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", NodeColors.EXEC, true));

        FunctionSignature signature = function.functionSignature();
        List<String> dataNames = new ArrayList<>();
        List<Class<?>> dataParameters = new ArrayList<>();
        List<Type> dataGenerics = new ArrayList<>();

        for (int i = 0; i < signature.parameters().size(); i++) {
            Class<?> type = signature.parameters().get(i);

            if (FlowFunctionNodeData.isCallback(type)) {
                addOutput(new Pin(ParseUtils.camelToSpacedPascal(signature.parameterNames().get(i)), NodeColors.EXEC, true));
                continue;
            }

            String label = function.isMember() && i == 0 ? "Target" : type.getSimpleName();
            addInput(new Pin(label, NodeColors.forType(type), false, type, signature.genericParameters().get(i)));

            dataNames.add(signature.parameterNames().get(i));
            dataParameters.add(type);
            dataGenerics.add(signature.genericParameters().get(i));
        }

        this.dataSignature = new FunctionSignature(
            dataNames, dataParameters, signature.returnType(), dataGenerics, signature.genericReturnType());

        Class<?> returnType = signature.returnType();
        if (returnType != void.class && returnType != Void.class) {
            this.returnPin = outputPinCount();
            addOutput(new Pin("Return Value", NodeColors.forType(returnType), false, returnType, signature.genericReturnType()));
        } else {
            this.returnPin = -1;
        }

        relayout();
    }

    public ScriptFunction function() {
        return function;
    }

    @Override
    protected void onConnectionsChanged() {
        bindTypeVariables(dataSignature, 1, returnPin);
    }

    @Override
    public NodeData toData() {
        return new FlowFunctionNodeData(function);
    }
}
