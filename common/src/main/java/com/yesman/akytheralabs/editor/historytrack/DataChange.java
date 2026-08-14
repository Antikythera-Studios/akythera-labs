package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;

import java.util.function.Consumer;

/// A history entry for a value change on a [DataBoundWidget]. It touches no file; the user saves
/// manually.
///
/// `setData` writes through to the bound holder and refreshes the widget, typically through
/// [DataBoundWidget#valueSetter]. The manager wraps the call in a re-entry guard, so a setter that
/// fires the change callback itself is safe here.
public record DataChange<T>(T oldValue, T newValue, Consumer<T> setData) implements WorkHistory {
    @Override
    public void undo() {
        setData().accept(oldValue());
    }

    @Override
    public void redo() {
        setData().accept(newValue());
    }
}
