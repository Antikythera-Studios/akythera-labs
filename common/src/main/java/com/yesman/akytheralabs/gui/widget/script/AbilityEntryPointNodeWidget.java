package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.world.ability.AbilityCueInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akytheralabs.gui.widget.script.node.EntryPointNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// A flow entry node for an ability or cue graph. Each event is a flow origin: an execution output
/// plus the objects the engine binds as locals before it runs the graph.
public final class AbilityEntryPointNodeWidget extends AbstractNodeWidget {
    private static final int EXEC_COLOR = 0xFFEEEEEE;
    private static final int OBJECT_COLOR = 0xFF4FA3D1;
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    /// Which graph the entry belongs to, which is what decides the objects it offers.
    ///
    /// The kind is recovered on load from the persisted output labels, so the labels are the
    /// identity and must match the locals the engine binds.
    public enum EntryKind {
        /// `component` and `instance`, bound by [com.yesman.akythera.core.blueprint.script.DataDrivenAbilityState].
        ABILITY(List.of("component", "instance"), List.of(AbilitySystemComponent.class, AbilityInstance.class)),

        /// `component`, `cue` and `state`, bound by
        /// [com.yesman.akythera.core.blueprint.script.DataDrivenCueBehavior].
        CUE(
            List.of("component", "cue", "state"),
            List.of(AbilitySystemComponent.class, AbilityCueInstance.class, AbilityState.class)
        );

        private final List<String> labels;
        private final List<Class<?>> types;

        EntryKind(final List<String> labels, final List<Class<?>> types) {
            this.labels = labels;
            this.types = types;
        }

        public List<String> labels() {
            return labels;
        }

        /// The kind whose labels `outLabels` carries, defaulting to [#ABILITY].
        public static EntryKind ofLabels(final List<String> outLabels) {
            return outLabels.contains("cue") ? CUE : ABILITY;
        }
    }

    private final EntryKind kind;

    public AbilityEntryPointNodeWidget(
        final Font font,
        final Component title,
        final EntryKind kind,
        final int graphX,
        final int graphY
    ) {
        super(font, title, graphX, graphY);

        this.kind = kind;
        setTitleColor(TITLE_COLOR);

        addOutput(new Pin("", EXEC_COLOR, true));

        for (int i = 0; i < kind.labels().size(); i++) {
            addOutput(new Pin(kind.labels().get(i), OBJECT_COLOR, false, kind.types.get(i)));
        }

        relayout();
    }

    public EntryKind entryKind() {
        return kind;
    }

    /// The flow's origin can never be removed — without it the graph has no entry.
    @Override
    public boolean isDeletable() {
        return false;
    }

    @Override
    public NodeData toData() {
        List<String> labels = new ArrayList<>(outputPinCount());

        for (int i = 0; i < outputPinCount(); i++) {
            labels.add(outputPin(i).label());
        }

        return new EntryPointNodeData(labels);
    }
}
