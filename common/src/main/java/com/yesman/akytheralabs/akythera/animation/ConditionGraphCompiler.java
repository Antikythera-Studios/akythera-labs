package com.yesman.akytheralabs.akythera.animation;

import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.LiteralNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.TransitionResultNodeData;
import org.jspecify.annotations.Nullable;

/// Lowers a standalone result canvas — one rooted at a [TransitionResultNodeData] — into a single
/// [PureNode].
///
/// The compiled tree runs headless, against a null `DataDrivenScript`, so nothing in it dereferences
/// the script: entry outputs lower to context reads of the execution context's locals, and `Self`
/// lowers to a context read of `ownerContextParam`.
///
/// [#compile] resolves a missing or unwired result to `false`; [#compileOptional] hands that case
/// back as null.
public final class ConditionGraphCompiler extends PureGraphCompiler {
    private final String ownerContextParam;

    private ConditionGraphCompiler(ScriptGraphData data, Class<?> ownerType, String ownerContextParam) {
        super(data, ownerType);
        this.ownerContextParam = ownerContextParam;
    }

    public static PureNode compile(ScriptGraphData graph, Class<?> ownerType, String ownerContextParam) {
        PureNode result = compileOptional(graph, ownerType, ownerContextParam);

        return result != null ? result : literalFalse();
    }

    /// The [PureNode] wired into the canvas' result root, or null when there is no root or nothing is
    /// wired into it.
    public static @Nullable PureNode compileOptional(
        ScriptGraphData graph, Class<?> ownerType, String ownerContextParam
    ) {
        ConditionGraphCompiler ctx = new ConditionGraphCompiler(graph, ownerType, ownerContextParam);

        for (GraphNode n : ctx.byId.values()) {
            if (n.data() instanceof TransitionResultNodeData) return ctx.compileInput(n.id(), 0);
        }

        return null;
    }

    /// The owner is a context local named by [#ownerContextParam].
    @Override
    public PureNode selfValue() {
        return new ContextVariableAccessNode(ownerContextParam);
    }

    /// Typed empty literal when the expected script type has one, else the owner context read.
    @Override
    public PureNode defaultValue(@Nullable Class<?> expectedType) {
        if (expectedType != null && expectedType != Object.class) {
            for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
                if (ParseUtils.box(st.type()) == ParseUtils.box(expectedType) && st.codec() != null) {
                    return new LiteralNode<>(st, LiteralNodeData.parse(st.type(), ""));
                }
            }
        }

        return selfValue();
    }

    private static PureNode literalFalse() {
        return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.FALSE);
    }
}
