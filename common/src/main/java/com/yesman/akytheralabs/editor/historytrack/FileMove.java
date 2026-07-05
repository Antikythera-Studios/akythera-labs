package com.yesman.akytheralabs.editor.historytrack;

/// File system change for moving files/directories
/// and any registry bookkeeping the entry created from the files.
public record FileMove(Runnable move, Runnable cancelMove) implements WorkHistory {
    @Override
    public void undo() {
        cancelMove.run();
    }

    @Override
    public void redo() {
        move.run();
    }
}
