package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A flow entry (event) node for an animator-script graph. Each event is a flow origin: an
/// execution output plus whatever runtime parameters the event is invoked with. The Event Graph
/// shows two — the per-tick **Update** event (which also exposes `frameTime` / `partialTick`) and
/// the once-at-init **Constructor** event (exec only). Visual only.
public final class EntryPointNodeWidget extends AbstractNodeWidget {
    private static final int EXEC_COLOR = 0xFFEEEEEE;
    private static final int FLOAT_COLOR = 0xFF6CCB5F;
    private static final int TITLE_COLOR = 0xFF5A1F1F;

    /// Which event the entry represents: [#UPDATE] runs every frame and supplies the frame-time
    /// params; [#CONSTRUCTOR] runs once at construction and supplies none. The kind is recovered on
    /// load from the persisted output labels (a row with data-output labels is an Update entry).
    public enum EntryKind {
        UPDATE, CONSTRUCTOR
    }

    private final EntryKind kind;

    /// Backwards-compatible overload — builds the [EntryKind#UPDATE] event.
    public EntryPointNodeWidget(Font font, Component title, int graphX, int graphY) {
        this(font, title, EntryKind.UPDATE, graphX, graphY);
    }

    public EntryPointNodeWidget(Font font, Component title, EntryKind kind, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        this.kind = kind;
        setTitleColor(TITLE_COLOR);

        // No inputs — this is the flow's origin. Output: exec flow, plus (Update only) the two
        // params the event is invoked with.
        addOutput(new Pin("", EXEC_COLOR, true));
        if (kind == EntryKind.UPDATE) {
            addOutput(new Pin("frameTime", FLOAT_COLOR, false, float.class));
            addOutput(new Pin("partialTick", FLOAT_COLOR, false, float.class));
        }

        relayout();
    }

    /// Which event this entry node represents.
    public EntryKind entryKind() {
        return kind;
    }

    /// The flow's origin can never be removed — without it the graph has no entry.
    @Override
    public boolean isDeletable() {
        return false;
    }
}
