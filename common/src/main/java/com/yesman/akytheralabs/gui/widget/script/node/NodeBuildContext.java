package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// What a [NodeData] gets to rebuild its widget: the canvas font, the row's graph-space position, and
/// the host screen's entry-node factory — an entry node's title and pin shape are event-specific,
/// which the row itself does not know.
public record NodeBuildContext(Font font, ScriptNodeGraphWidget.EntryNodeFactory entryFactory, int x, int y) {
    /// The host's entry widget for this row's position and output labels, or null when the hosting
    /// canvas has no entry node of its own.
    public @Nullable AbstractNodeWidget entryWidget(List<String> outLabels) {
        return entryFactory.create(x, y, outLabels);
    }
}
