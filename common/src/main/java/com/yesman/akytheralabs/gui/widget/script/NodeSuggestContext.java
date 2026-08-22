package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

/// Describes what kind of node a dropped wire (or an empty-canvas right-click) is asking for, so the host
/// screen can tailor the [NodeSuggestModal] entries to the gesture (UE hides incompatible nodes the same
/// way).
///
/// The three category flags select *which families* of suggestions appear, and are not
/// mutually exclusive (a right-click on empty canvas shows flow nodes **and** pure
/// functions at once):
///
/// - `flow`: structural flow nodes (Sequence / Branch), variable setters, and **non-pure**
///   [ScriptFunction]s.
/// - `valueProviders`: the Self node, inline Literals, and variable getters — the value
///   sources offered when a **data input** asks to be filled.
/// - `pureFunctions`: **pure** value-returning [ScriptFunction]s.
///
/// `expectedType` is the type a dragged data **input** pin wants to receive (null = untyped).
/// It drives the literal/variable-getter offering and the value-provider return-type check,
/// and is also what the host uses to color the connection curve.
///
/// `providedType` is the type a dragged data **output** pin emits (null = untyped / not an
/// output drag). It lets the host reject built-in nodes that cannot consume the dragged value
/// — e.g. the numeric operator / comparator nodes, which would otherwise slip past
/// `functionFilter` because they are not [ScriptFunction]s.
///
/// `functionFilter` is the per-function predicate applied on top of the pure/non-pure split. It
/// carries requirements the context knows nothing about — "accepts the dragged pin type as a
/// parameter", "takes the script owner" — which `expectedType` alone cannot express.
///
/// `usage` is the set of [ScriptFunction.Usage]s the hosting canvas accepts — the "Event Graph" is
/// [ScriptFunction.Usage#SCRIPT]-only, while the "Anim Graph" / pose graphs accept both
/// [ScriptFunction.Usage#ANIM_GRAPH] (pose nodes) and `SCRIPT` (pure functions, e.g. float math for a
/// BlendSpace), mirroring UE's per-graph node palette. The factory methods default it to `{SCRIPT}`;
/// the host canvas re-stamps it via [#withUsage] before the suggestion provider runs (so each
/// [ScriptNodeGraphWidget] decides the usage, not the gesture).
///
/// `dataFlowOnly` marks a canvas that has no execution flow — a pure value/pose data-flow graph
/// (the Anim Graph, a state's pose graph, a transition's condition graph). On such a canvas the
/// execution-flow constructs (Sequence / Branch / variable setters) are suppressed. Stamped by the
/// host alongside `usage` via [#withDataFlowOnly]; defaults to `false` (the Event Graph).
public record NodeSuggestContext(
    boolean flow,
    boolean valueProviders,
    boolean pureFunctions,
    @Nullable Class<?> expectedType,
    @Nullable Class<?> providedType,
    Predicate<ScriptFunction> functionFilter,
    Set<ScriptFunction.Usage> usage,
    boolean dataFlowOnly
) {
    /// Execution-pin drag: flow nodes only, with no function-level filtering.
    public static NodeSuggestContext execution() {
        return new NodeSuggestContext(true, false, false, null, null, _ -> true, Set.of(ScriptFunction.Usage.SCRIPT), false);
    }

    /// Data-input drag: value providers plus pure functions whose return type fits
    /// `expectedType`.
    public static NodeSuggestContext value(@Nullable Class<?> expectedType) {
        return new NodeSuggestContext(false, true, true, expectedType, null, _ -> true, Set.of(ScriptFunction.Usage.SCRIPT), false);
    }

    /// Data-output (parameter) drag: the value the dragged pin emits is fed into the suggested
    /// node, so both pure functions **and** flow nodes (non-pure functions, variable setters)
    /// that accept `providedType` in some parameter slot are offered. The non-value-consuming
    /// structural flow nodes (Sequence / Branch) and the value-producing member getters are
    /// suppressed by the `providedType`-aware host logic.
    public static NodeSuggestContext parameter(@Nullable Class<?> providedType) {
        return new NodeSuggestContext(true, false, true, null, providedType, acceptsParameter(providedType), Set.of(ScriptFunction.Usage.SCRIPT), false);
    }

    /// Right-click on empty canvas: every flow node and pure function, unfiltered — the full
    /// catalogue of actions, since a free-standing spawn carries no pin to constrain it.
    public static NodeSuggestContext entryPoint() {
        return new NodeSuggestContext(true, false, true, null, null, _ -> true, Set.of(ScriptFunction.Usage.SCRIPT), false);
    }

    /// Re-stamps the accepted [ScriptFunction.Usage]s — called by the host [ScriptNodeGraphWidget]
    /// so the suggestions are scoped to its graph (Event Graph → `SCRIPT`, Anim Graph →
    /// `ANIM_GRAPH, SCRIPT`) regardless of which gesture built the context.
    public NodeSuggestContext withUsage(Set<ScriptFunction.Usage> usage) {
        return new NodeSuggestContext(flow, valueProviders, pureFunctions, expectedType, providedType, functionFilter, usage, dataFlowOnly);
    }

    /// Re-stamps whether the host canvas is data-flow-only (no execution constructs).
    public NodeSuggestContext withDataFlowOnly(boolean dataFlowOnly) {
        return new NodeSuggestContext(flow, valueProviders, pureFunctions, expectedType, providedType, functionFilter, usage, dataFlowOnly);
    }

    /// A function filter matching any function with a parameter slot that accepts `type`,
    /// boxing- and Number-coercion-aware via [FunctionSignature#isParameterCompatible]. A
    /// null type (untyped pin) matches every function.
    private static Predicate<ScriptFunction> acceptsParameter(@Nullable Class<?> type) {
        if (type == null) return _ -> true;

        return function -> {
            FunctionSignature signature = function.functionSignature();
            for (int i = 0; i < signature.parameters().size(); i++) {
                if (signature.isParameterCompatible(type, i)) return true;
            }
            return false;
        };
    }
}
