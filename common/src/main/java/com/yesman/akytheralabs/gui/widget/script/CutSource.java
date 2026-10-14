package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.editor.NodeClipboard;

/// A canvas whose cut nodes wait on it for the [NodeClipboard]'s paste.
public interface CutSource {
    /// Releases the cut nodes where they are.
    void clearCut();

    /// Deletes the cut nodes as one undoable unit. They stay marked as cut.
    void deleteCut();
}
