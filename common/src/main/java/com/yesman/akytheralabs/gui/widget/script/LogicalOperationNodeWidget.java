package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A binary boolean logic node (AND / OR / NAND / NOR / XOR): two boolean inputs, one boolean output.
/// Used in the Event Graph and state-machine transition conditions to combine predicates, the binary
/// companion to the unary [NotNodeWidget].
///
/// The [#operator] is fixed at creation. Compile rule: emit the engine `LogicalOperationNode(bool1,
/// bool2, operator)` over the two wired-in booleans (see the graph compilers).
public final class LogicalOperationNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private final Operator operator;

    public LogicalOperationNodeWidget(Font font, Operator operator, int graphX, int graphY) {
        super(font, Component.literal(operator.symbol()), graphX, graphY);

        this.operator = operator;

        setTitleColor(TITLE_COLOR);
        // Two boolean inputs and the boolean result — fixed-type (unlike the type-detecting math nodes).
        addInput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));
        addInput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));
        addOutput(new Pin("", NodeColors.forType(boolean.class), false, boolean.class));

        relayout();
    }

    public Operator operator() {
        return operator;
    }

    /// The supported logic operators. `symbol` is the node-title glyph; `menuLabel` is the
    /// suggestion-list entry. Names match the engine `LogicalOperationNode.Operator`.
    public enum Operator {
        AND("AND", "And (AND)"),
        OR("OR", "Or (OR)"),
        NAND("NAND", "Nand (NAND)"),
        NOR("NOR", "Nor (NOR)"),
        XOR("XOR", "Xor (XOR)");

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
