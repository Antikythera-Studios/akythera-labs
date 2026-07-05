package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Widget for a [com.yesman.akythera.api.data.modularize.node.PureFunctionNode]:
/// evaluates a single pure [ScriptFunction] with no side effects, so it carries **no
/// execution pins**. The left side has one data input per parameter, labeled from
/// [FunctionSignature#parameterNames] (the owner slot of a member function shows as
/// "Target"); the right side has the single return-value output. Hovering the title
/// shows the function's description plus its parameter names. Visual only.
public final class PureFunctionNodeWidget extends AbstractNodeWidget {
    private final ScriptFunction function;
    @Nullable
    private final List<Component> tooltip;

    public PureFunctionNodeWidget(Font font, ScriptFunction function, int graphX, int graphY) {
        super(font, Component.literal(function.metadata().label()), graphX, graphY);

        this.function = function;

        FunctionSignature signature = function.functionSignature();
        setTitleColor(NodeColors.titleTint(signature.returnType()));

        List<Class<?>> parameters = signature.parameters();
        List<String> names = signature.parameterNames();
        for (int i = 0; i < parameters.size(); i++) {
            Class<?> type = parameters.get(i);
            String label = function.isMember() && i == 0
                ? "Target"
                : (i < names.size() ? names.get(i) : type.getSimpleName());
            addInput(new Pin(label, NodeColors.forType(type), false, type));
        }

        Class<?> returnType = signature.returnType();
        addOutput(new Pin("", NodeColors.forType(returnType), false, returnType));

        relayout();

        this.tooltip = buildTooltip(function, names);
    }

    /// Only shows a tooltip when the function carries a description (per the design),
    /// appending the parameter-name list so the unlabeled member "Target" / abbreviated
    /// pins are still discoverable.
    private static @Nullable List<Component> buildTooltip(ScriptFunction function, List<String> names) {
        String description = function.metadata().description();
        if (description == null) return null;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(description));
        if (!names.isEmpty()) {
            lines.add(Component.literal("Parameters: " + String.join(", ", names)));
        }
        return lines;
    }

    @Override
    public @Nullable List<Component> headerTooltipLines() {
        return tooltip;
    }

    public ScriptFunction function() {
        return function;
    }
}
