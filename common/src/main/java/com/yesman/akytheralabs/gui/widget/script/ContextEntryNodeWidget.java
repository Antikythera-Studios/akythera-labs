package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.EntryPointNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// The origin node of a standalone condition canvas: **data outputs only** (one per [ContextParam]), no
/// execution pin — condition graphs are pure data-flow. Persists as an [EntryPointNodeData] row (labels =
/// param names) and rebuilds through the screen-supplied `EntryNodeFactory`, which re-types the pins from the
/// field's declared params.
public final class ContextEntryNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF5A1F1F;

    public ContextEntryNodeWidget(Font font, Component title, List<ContextParam> params, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);

        for (ContextParam param : params) {
            addOutput(new Pin(param.name(), NodeColors.forTypeOrWildcard(param.type()), false, param.type()));
        }

        relayout();
    }

    /// Rebuilds the stored row of a canvas carrying `params`, re-typing its pins by label. Pin types are
    /// structural (not persisted), and an unknown label degrades to a wildcard pin.
    public static ScriptNodeGraphWidget.EntryNodeFactory factory(final Font font, final List<ContextParam> params) {
        return (x, y, labels) -> new ContextEntryNodeWidget(
            font, Component.literal("Params"),
            labels.stream()
                .map(label -> params.stream()
                    .filter(param -> param.name().equals(label))
                    .findFirst()
                    .orElse(new ContextParam(label, Object.class)))
                .toList(),
            x, y
        );
    }

    /// The canvas' origin can never be removed.
    @Override
    public boolean isDeletable() {
        return false;
    }

    /// One context local the canvas can read: pin label == the `ScriptVariables` name the runtime
    /// binds (e.g. `equipper`), typed so pin drags filter pure-function suggestions.
    public record ContextParam(String name, Class<?> type) {}

    @Override
    public NodeData toData() {
        List<String> labels = new ArrayList<>(outputPinCount());
        for (int i = 0; i < outputPinCount(); i++) labels.add(outputPin(i).label());

        return new EntryPointNodeData(labels);
    }
}
