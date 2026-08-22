package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.TypeConvertNode;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.TypeConvertNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A numeric type-conversion node (UE's autocast / "To Float" node): one input pin of the source type and one
/// output pin of the target type. It performs an **explicit** (narrowing) cast — e.g. `double -> float`,
/// `float -> int` — the casts listed in the source type's [ScriptType#explicitCastableTypes]. Widening casts
/// (`float -> double`) are implicit and need no node.
///
/// Spawned two ways: dropping a wire from a pin onto empty canvas offers a "Convert to X" entry per castable
/// target (see [AnimatorScriptEditorScreen]), and dropping a wire straight onto a pin of a
/// different-but-castable type auto-inserts one between the two pins (see [ScriptNodeGraphWidget]).
///
/// Compile rule (see the graph compilers): lowers to an engine [TypeConvertNode] carrying the `from` / `to`
/// [ScriptType]s and the [PureNode] wired into its input.
public final class TypeConvertNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private final Class<?> fromType;
    private final Class<?> toType;

    public TypeConvertNodeWidget(Font font, Class<?> fromType, Class<?> toType, int graphX, int graphY) {
        super(font, Component.literal(typeName(fromType) + " -> " + typeName(toType)), graphX, graphY);

        this.fromType = fromType;
        this.toType = toType;

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("", NodeColors.forType(fromType), false, fromType));
        addOutput(new Pin("", NodeColors.forType(toType), false, toType));

        relayout();
    }

    /// The cast's source java type — this node's input pin type and the `from` of the emitted
    /// [TypeConvertNode].
    public Class<?> fromType() {
        return fromType;
    }

    /// The cast's target java type — this node's output pin type and the `to` of the emitted
    /// [TypeConvertNode].
    public Class<?> toType() {
        return toType;
    }

    /// The lowercase keyword name for a (boxed or primitive) numeric type used in the node title and
    /// the "Convert to X" suggestion labels; any other type falls back to its simple class name.
    public static String typeName(Class<?> type) {
        if (type == byte.class || type == Byte.class) return "byte";
        if (type == short.class || type == Short.class) return "short";
        if (type == int.class || type == Integer.class) return "int";
        if (type == long.class || type == Long.class) return "long";
        if (type == float.class || type == Float.class) return "float";
        if (type == double.class || type == Double.class) return "double";
        return type.getSimpleName();
    }

    @Override
    public NodeData toData() {
        return new TypeConvertNodeData(scriptTypeOf(fromType), scriptTypeOf(toType));
    }
}
