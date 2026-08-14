package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A node's view of the canvas while it lowers itself: the canvas services bound to the lowering
/// node's own row id, so a node reads its inputs by pin index alone.
public record NodeInputs(PureCompileContext ctx, int id) {
    /// The value wired into data input `pin`, or null when unwired.
    public @Nullable PureNode value(int pin) {
        return ctx.compileInput(id, pin);
    }

    /// The value wired into data input `pin`, or the canvas' default for `expectedType`.
    public PureNode valueOr(int pin, @Nullable Class<?> expectedType) {
        return ctx.compileInputOr(id, pin, expectedType);
    }

    /// One value per parameter of `function`, from the inputs starting at `inputOffset`.
    public List<PureNode> params(ScriptFunction function, int inputOffset) {
        return ctx.compileParams(id, function, inputOffset);
    }

    /// The row feeding data input `pin`, or null when unwired.
    public @Nullable GraphNode source(int pin) {
        return ctx.sourceNode(id, pin);
    }
}
