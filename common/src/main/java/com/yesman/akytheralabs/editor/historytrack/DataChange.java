package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;

import java.util.function.Consumer;

/// A history for changing [DataBoundWidget].
///
/// This task doesn't touch any file system side change. Users needs to manually
/// call `Save` option.
///
/// `setData` should write through to the bound holder AND refresh the widget
/// (typically via [DataBoundWidget#valueSetter]). The manager wraps the call
/// with a re-entry guard so it's safe to use a setter that itself fires the
/// change callback.
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
