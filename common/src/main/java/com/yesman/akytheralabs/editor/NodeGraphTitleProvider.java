package com.yesman.akytheralabs.editor;

import net.minecraft.network.chat.Component;

/// Implemented by objects owning an [EditorNodeGraph]-annotated field to title the graph's center
/// tab dynamically (e.g. `"one_hand condition"` from a sibling stance combo) instead of the static
/// [EditorEditable] label. Queried at button-press time, so the title reflects the owner's state
/// when the tab opens; an already-open tab keeps the title it opened with.
public interface NodeGraphTitleProvider {
    /// The tab title for the graph stored in field `fieldName`; return `fallback` (the row label)
    /// for fields this provider doesn't cover.
    Component nodeGraphTabTitle(String fieldName, Component fallback);
}