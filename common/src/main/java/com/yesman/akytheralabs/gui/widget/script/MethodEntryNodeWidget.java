package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.MethodEntryNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// The entry node of a blueprint method or function: an execution output, then one output per parameter,
/// named after it. An entry whose method resolves to nothing, such as an event the parent no longer
/// declares, keeps only its execution output.
public final class MethodEntryNodeWidget extends AbstractNodeWidget {
    private static final int EVENT_TITLE_COLOR = 0xFF5A1F1F;
    private static final int FUNCTION_TITLE_COLOR = 0xFF1F3A5A;

    private final String method;
    private final @Nullable BlueprintSignature signature;

    public MethodEntryNodeWidget(Font font, String method, @Nullable BlueprintSignature signature, int graphX, int graphY) {
        super(font, signature == null ? Component.literal(method) : signature.title(), graphX, graphY);

        this.method = method;
        this.signature = signature;

        setTitleColor(signature != null && signature.kind() == BlueprintSignature.Kind.EVENT
            ? EVENT_TITLE_COLOR
            : FUNCTION_TITLE_COLOR);

        addOutput(new Pin("", NodeColors.EXEC, true));

        if (signature != null) {
            for (BlueprintSignature.Parameter parameter : signature.parameters()) {
                addOutput(new Pin(parameter.name(), NodeColors.forType(parameter.type()), false, parameter.type()));
            }
        }

        relayout();
    }

    public String method() {
        return method;
    }

    /// An event's entry may be removed and placed again from the node suggestions. A function's entry stays
    /// while its function exists.
    @Override
    public boolean isDeletable() {
        return signature == null || signature.kind() == BlueprintSignature.Kind.EVENT;
    }

    /// A method has one entry, so a copy of it is never coherent.
    @Override
    public boolean isCopyable() {
        return false;
    }

    @Override
    public List<Component> intrinsicErrors() {
        return signature == null
            ? List.of(Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_UNKNOWN_METHOD, method))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new MethodEntryNodeData(method);
    }
}
