package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// A numeric comparison node, emitted as a pure boolean-returning node. Unlike a plain
/// [PureFunctionNodeWidget] its two value inputs are **type-detecting**: in the initial
/// "wildcard" state both accept any type; the first value wired in fixes the node's type,
/// after which the still-open input only accepts that same type (enforced through the shared
/// [AbstractNodeWidget#pinsCompatible] check). Disconnecting every input reverts to wildcard.
///
/// The comparison [#operator] (>, >=, <, <=, ==) is fixed at creation. The `Comparator` the
/// engine function needs is not user-facing — it is hardcoded at serialization from the
/// detected numeric type (see the graph compilers).
public final class ComparatorNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;
    private final Operator operator;

    /// The decided value type, or null while in wildcard (type-detecting) mode.
    @Nullable
    private Class<?> detectedType;

    public ComparatorNodeWidget(Font font, Operator operator, int graphX, int graphY) {
        super(font, Component.literal(operator.symbol()), graphX, graphY);

        this.operator = operator;

        setTitleColor(TITLE_COLOR);

        // Two type-detecting value inputs (wildcard until the first connection) ...
        addInput(wildcardPin());
        addInput(wildcardPin());
        // ... and the boolean result.
        addOutput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));

        relayout();
    }

    private static Pin wildcardPin() {
        return new Pin("", NodeColors.WILDCARD, false, null);
    }

    /// Re-types both value inputs from whatever is wired in: the first connected source decides
    /// the type, and once decided the still-open input only accepts that type. When nothing is
    /// connected, reverts to wildcard.
    @Override
    protected void onConnectionsChanged() {
        Class<?> detected = null;
        for (Pin source : connectedInputSources()) {
            detected = source.type();
            break;
        }

        if (Objects.equals(detected, detectedType)) return;

        detectedType = detected;
        int color = NodeColors.forTypeOrWildcard(detected);
        setInputPin(0, new Pin("", color, false, detected));
        setInputPin(1, new Pin("", color, false, detected));
        relayout();
    }

    public Operator operator() {
        return operator;
    }

    /// The supported comparison operators. `symbol` is the node-title glyph; `menuLabel` is
    /// the suggestion-list entry.
    public enum Operator {
        LESS("<", "Less (<)"),
        LESS_EQUAL("<=", "Less or Equal (<=)"),
        GREATER(">", "Greater (>)"),
        GREATER_EQUAL(">=", "Greater or Equal (>=)"),
        EQUAL("==", "Equal (==)");

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
