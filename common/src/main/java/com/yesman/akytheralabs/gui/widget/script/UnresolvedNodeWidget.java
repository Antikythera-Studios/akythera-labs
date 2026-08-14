package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Stand-in for a persisted graph node that failed to decode (unknown kind / function / type
/// after a refactor). Inert: no pins, no editing — it exists so the row stays visible, red, and
/// round-trips untouched on save ([ScriptNodeGraphWidget#exportGraph] re-emits its raw JSON).
/// Deleting it deletes the row — and with it the file's unreadable content, at the user's
/// explicit choice. Any presence of one is a hard compile error (see the graph compilers).
public final class UnresolvedNodeWidget extends AbstractNodeWidget {
    private final ScriptGraphData.FailedNode data;

    public UnresolvedNodeWidget(Font font, ScriptGraphData.FailedNode data) {
        super(font, Component.literal("Unresolved node"), data.x(), data.y());
        this.data = data;
        super.setErrors(List.of(Component.literal(data.message())));
    }

    public ScriptGraphData.FailedNode data() {
        return data;
    }

    /// Permanently error-marked: the failure never clears, so `validate()`'s blanket
    /// clear-then-flag pass cannot un-red this node.
    @Override
    public void setErrors(@Nullable List<Component> errors) {
        if (errors == null || errors.isEmpty()) return;
        super.setErrors(errors);
    }

    /// A decode failure, not a node type: the canvas re-emits this row's raw JSON and never
    /// asks it to describe itself.
    @Override
    public NodeData toData() {
        throw new UnsupportedOperationException(
            "An unresolved node re-emits its raw row; see ScriptNodeGraphWidget#exportGraph");
    }
}
