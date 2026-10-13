package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akytheralabs.editor.historytrack.WorkHistory;

/// The playhead of a track panel moved by the user, as one undo step that is no unsaved change.
///
/// @param from the frame the playhead was at
/// @param to   the frame it was moved to
public record PlayheadMove(TrackTimeline timeline, float from, float to) implements WorkHistory {
    @Override
    public void undo() {
        timeline.setCurrentFrame(from);
    }

    @Override
    public void redo() {
        timeline.setCurrentFrame(to);
    }

    @Override
    public boolean changesData() {
        return false; // a view setting
    }
}
