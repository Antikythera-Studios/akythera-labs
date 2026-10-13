package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akytheralabs.editor.EditorWidgetFactory;

/// An editor object whose changes are recorded under something else's history scope, see
/// [HistoryManager#pushHistory(Object, WorkHistory)]. [EditorWidgetFactory] records a change to a slot under
/// its owner, or under the owner's [#historyScope] when it has one.
public interface HistoryScoped {
    /// The scope this object's changes are recorded under.
    Object historyScope();
}
