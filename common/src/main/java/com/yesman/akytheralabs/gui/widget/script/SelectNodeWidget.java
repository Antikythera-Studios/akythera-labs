package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PureFunctionNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// Yields one of two values by a boolean, emitted as a pure node over the `akythera:select`
/// [ScriptFunction]. Unlike a plain [PureFunctionNodeWidget] its two branch inputs and its result are
/// **type-detecting**: in the initial "wildcard" state they accept any type; the first concretely
/// typed connection fixes the node's type, after which the still-open branch only accepts that same
/// type (enforced through the shared [AbstractNodeWidget#pinsCompatible] check). Disconnecting every
/// typed connection reverts to wildcard.
///
/// The engine function is untyped on both branches — it picks a reference and nothing more — so the
/// type a graph sees is the one these pins agree on.
public final class SelectNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;
    private static final String CONDITION_LABEL = "Condition";
    private static final String WHEN_TRUE_LABEL = "True";
    private static final String WHEN_FALSE_LABEL = "False";

    /// Input pin index of the branch inputs and of the boolean that picks between them.
    private static final int CONDITION_PIN = 0;
    private static final int WHEN_TRUE_PIN = 1;
    private static final int WHEN_FALSE_PIN = 2;

    private final ScriptFunction function;

    /// The decided branch type, or null while in wildcard (type-detecting) mode.
    @Nullable
    private Class<?> detectedType;

    public SelectNodeWidget(Font font, ScriptFunction function, int graphX, int graphY) {
        super(font, Component.literal(function.metadata().label()), graphX, graphY);

        this.function = function;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin(CONDITION_LABEL, NodeColors.forType(boolean.class), false, boolean.class));
        addInput(wildcardPin(WHEN_TRUE_LABEL));
        addInput(wildcardPin(WHEN_FALSE_LABEL));
        addOutput(wildcardPin(""));

        relayout();
    }

    /// The `akythera:select` function this node lowers to, so the graph exports it as an ordinary
    /// pure-function node.
    public ScriptFunction function() {
        return function;
    }

    private static Pin wildcardPin(String label) {
        return new Pin(label, NodeColors.WILDCARD, false, null);
    }

    /// Re-types both branches and the result from whatever is wired in. The condition pin is boolean
    /// throughout and takes no part in the detection.
    @Override
    protected void onConnectionsChanged() {
        Class<?> detected = detectType();

        if (Objects.equals(detected, detectedType)) return;

        detectedType = detected;
        int color = NodeColors.forTypeOrWildcard(detected);
        setInputPin(WHEN_TRUE_PIN, new Pin(WHEN_TRUE_LABEL, color, false, detected));
        setInputPin(WHEN_FALSE_PIN, new Pin(WHEN_FALSE_LABEL, color, false, detected));
        setOutputPin(0, new Pin("", color, false, detected));
        relayout();
    }

    /// The branch type, guessed from the first concretely-typed connection: a value wired into either
    /// branch takes priority, otherwise the typed input slot the result is wired into. Null while
    /// every connection is still wildcard (type-detecting) or nothing is connected.
    private @Nullable Class<?> detectType() {
        for (int pin : new int[] { WHEN_TRUE_PIN, WHEN_FALSE_PIN }) {
            Pin source = connectedInputSource(pin);
            if (source != null && source.type() != null) return source.type();
        }

        for (Pin target : connectedOutputTargets()) {
            if (target.type() != null) return target.type();
        }

        return null;
    }

    /// An ordinary pure function; only this widget is type-detecting.
    @Override
    public NodeData toData() {
        return new PureFunctionNodeData(function);
    }
}
