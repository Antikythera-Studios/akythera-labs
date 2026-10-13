package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.FunctionCallNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Calls a function the blueprint declares: execution in and out, one input per parameter, and a "Return
/// Value" output when it returns one. A call whose function is gone keeps only its execution pins.
public final class FunctionCallNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final String function;
    private final @Nullable BlueprintSignature signature;

    public FunctionCallNodeWidget(Font font, String function, @Nullable BlueprintSignature signature, int graphX, int graphY) {
        super(font, signature == null ? Component.literal(function) : signature.title(), graphX, graphY);

        this.function = function;
        this.signature = signature;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addOutput(new Pin("", NodeColors.EXEC, true));

        if (signature != null) {
            for (BlueprintSignature.Parameter parameter : signature.parameters()) {
                addInput(new Pin(parameter.name(), NodeColors.forType(parameter.type()), false, parameter.type()));
            }

            if (signature.returnType() != null) {
                addOutput(new Pin("Return Value", NodeColors.forType(signature.returnType()), false, signature.returnType()));
            }
        }

        relayout();
    }

    public String function() {
        return function;
    }

    @Override
    public List<Component> intrinsicErrors() {
        return signature == null || signature.kind() != BlueprintSignature.Kind.FUNCTION
            ? List.of(Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_UNKNOWN_METHOD, function))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new FunctionCallNodeData(function);
    }
}
