package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// A basic arithmetic node (+, -, *, /), emitted as a pure numeric-returning node. Like
/// [ComparatorNodeWidget] its two value inputs and its result output are **type-detecting**:
/// they start as wildcard and accept any type. The type is fixed by the first concretely-typed
/// connection on **either side** — a value wired into an input, or the typed input slot the
/// result is wired into — after which all three pins take that type and the open inputs only
/// accept it (via [AbstractNodeWidget#pinsCompatible]). Detecting from the result connection
/// covers the case where only the output is wired (e.g. the result feeds a float slot, so the
/// operands must be float). Disconnecting every wire reverts to wildcard.
///
/// The [#operator] (+, -, *, /) is fixed at creation. This widget keeps both operands one type
/// so the on-canvas result type is well-defined; mixed-width operands (e.g. int + float) are
/// widened at runtime by the engine `BasicOperatorNode` (see the graph compilers).
public final class OperatorNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private final Operator operator;

    /// The decided value type, or null while in wildcard (type-detecting) mode.
    @Nullable
    private Class<?> detectedType;

    public OperatorNodeWidget(Font font, Operator operator, int graphX, int graphY) {
        super(font, Component.literal(operator.symbol()), graphX, graphY);

        this.operator = operator;

        setTitleColor(TITLE_COLOR);

        // Two type-detecting value inputs and a result output, all wildcard until the first
        // connection decides the type.
        addInput(wildcardPin());
        addInput(wildcardPin());
        addOutput(wildcardPin());

        relayout();
    }

    private static Pin wildcardPin() {
        return new Pin("", NodeColors.WILDCARD, false, null);
    }

    /// Re-types both value inputs and the result output from whatever is wired in: the type is
    /// decided by the first concretely-typed connection on either side (an input source, else the
    /// result's destination slot), and once decided the still-open input only accepts that type.
    /// When nothing concrete is connected, reverts to wildcard.
    @Override
    protected void onConnectionsChanged() {
        Class<?> detected = detectType();

        if (Objects.equals(detected, detectedType)) return;

        detectedType = detected;
        int color = NodeColors.forTypeOrWildcard(detected);
        setInputPin(0, new Pin("", color, false, detected));
        setInputPin(1, new Pin("", color, false, detected));
        setOutputPin(0, new Pin("", color, false, detected));
        relayout();
    }

    /// The node's value type, guessed from the first concretely-typed connection: a value wired
    /// into an input takes priority, otherwise the typed input slot the result is wired into.
    /// Null while every connection is still wildcard (type-detecting) or nothing is connected.
    private @Nullable Class<?> detectType() {
        for (Pin source : connectedInputSources()) {
            if (source.type() != null) return source.type();
        }

        for (Pin target : connectedOutputTargets()) {
            if (target.type() != null) return target.type();
        }

        return null;
    }

    public Operator operator() {
        return operator;
    }

    /// The supported arithmetic operators. `symbol` is the node-title glyph; `menuLabel` is the
    /// suggestion-list entry.
    public enum Operator {
        ADD("+", "Add (+)"),
        SUBTRACT("-", "Subtract (-)"),
        MULTIPLY("*", "Multiply (*)"),
        DIVIDE("/", "Divide (/)");

        private final String symbol;
        private final String menuLabel;

        Operator(String symbol, String menuLabel) {
            this.symbol = symbol;
            this.menuLabel = menuLabel;
        }

        public String symbol() { return symbol; }
        public String menuLabel() { return menuLabel; }
    }
}
