package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintMembers;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// The lowering services one canvas offers the nodes on it. A [NodeData] reaches these through
/// [NodeInputs]; the defaults below are what a canvas overrides to change how a shared node type
/// behaves there.
public interface PureCompileContext {
    /// The [PureNode] wired into data input `(id,pin)`, or null when unwired or unlowerable.
    @Nullable PureNode compileInput(int id, int pin);

    /// As [#compileInput] but substitutes [#defaultValue] when unwired or unlowerable.
    PureNode compileInputOr(int id, int pin, @Nullable Class<?> expectedType);

    /// One [PureNode] per signature parameter of `function`, read from `id`'s data inputs starting at
    /// `inputOffset` — 0 for a pure node, 1 for a flow node whose pin 0 is execution.
    List<PureNode> compileParams(int id, ScriptFunction function, int inputOffset);

    /// The row feeding data input `(id,pin)` after following reroutes, or null when unwired.
    @Nullable GraphNode sourceNode(int id, int pin);

    /// The stand-in for an unwired (or unlowerable) data input of `expectedType`.
    PureNode defaultValue(@Nullable Class<?> expectedType);

    /// The [ScriptCanvasKeys] key of the canvas being lowered, stamped onto the sub-trees whose evaluation is
    /// deferred past the entry point that builds them. [ScriptNode#NO_CANVAS] on a canvas that defers
    /// nothing.
    default String canvasKey() {
        return ScriptNode.NO_CANVAS;
    }

    /// The script owner as a value. A headless canvas overrides this with a context read, since
    /// [SelfNode] dereferences the script.
    default PureNode selfValue() {
        return SelfNode.INSTANCE;
    }

    /// The named state machine as a driver, or null on a canvas that hosts none.
    default @Nullable PureNode stateMachine(String name) {
        return null;
    }

    /// The members of the blueprint the canvas belongs to.
    default BlueprintMembers members() {
        return BlueprintMembers.NONE;
    }

    /// The method or function the canvas implements, or null for a canvas that implements none.
    default @Nullable BlueprintSignature enclosingFunction() {
        return null;
    }
}
